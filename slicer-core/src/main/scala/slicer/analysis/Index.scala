/*
 * Copyright 2026 io.github.jbwheatley
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package slicer.analysis

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.annotation.tailrec
import scala.meta.*
import scala.meta.internal.semanticdb

import slicer.model.*

import cats.syntax.alternative.*
import cats.syntax.either.*
import cats.syntax.eq.*
import cats.syntax.foldable.*
import cats.syntax.functorFilter.*

private[slicer] final case class Index(
    defs: Map[Symbol, DefNode],
    defsByFile: Map[Path, Vector[DefNode]],
    edges: Map[Symbol, Set[Symbol]],
    overriddenBy: Map[Symbol, Set[Symbol]],
    overrides: Map[Symbol, Set[Symbol]],
    supertypes: Map[Symbol, Set[Symbol]],
    instantiations: Map[Symbol, Set[Symbol]],
    structuralUses: Map[Symbol, Set[Symbol]],
    trees: Map[Path, Tree],
    derivations: Map[Symbol, Set[String]],
    factoryTargets: Map[Symbol, Set[Symbol]],
    exports: Map[Symbol, Set[Symbol]],
    flags: Map[Symbol, Set[DefFlag]],
    macroImplementations: Set[Symbol],
    reflectiveTargets: Set[Symbol],
    sources: Map[Path, String],
    warnings: Vector[String]
) {

  lazy val constructorParams: Map[Symbol, Vector[Symbol]] = {
    val owned: Vector[(Symbol, DefNode)] = for {
      param <- defs.values.toVector.filter(_.kind === DefKind.Param)
      owner <- param.owner
    } yield owner -> param

    owned
      .groupMap(_._1)(_._2)
      .view
      .mapValues(_.sortBy(_.start).map(_.symbol))
      .toMap
  }

  lazy val byDisplayName: Map[String, Vector[DefNode]] =
    defs.values.toVector.groupBy(_.displayName)

  lazy val byDottedName: Map[String, Vector[DefNode]] =
    defs.values.toVector.groupBy(_.dottedName)

  lazy val byPackage: Map[String, Vector[DefNode]] =
    defs.values.toVector.groupBy(node => node.symbol.toPackagePrefix)

  lazy val membersByOwner: Map[Symbol, Set[DefNode]] =
    Index.groupByOwner(defs.values.toVector.mapFilter(node => node.owner.map(_ -> node)))

  def isFlagged(symbol: Symbol, flag: DefFlag): Boolean = flags.getOrElse(symbol, Set.empty).contains(flag)

  def symbolsWithFlag(flag: DefFlag): Set[Symbol] = flags.keySet.filter(symbol => isFlagged(symbol, flag))

  def resolveQuery(query: String): Vector[DefNode] =
    defs.get(Symbol(query)) match {
      case Some(exactSymbol) => Vector(exactSymbol)
      case None =>
        val wanted = query.replace('#', '.').replace('/', '.')
        val exact = defs.values.filter(_.dottedName === wanted)
        if (exact.nonEmpty) exact.toVector
        else defs.values.filter(_.dottedName.endsWith("." + wanted)).toVector
    }
}

private[slicer] object Index {

  private def groupByOwner[A](pairs: Vector[(Symbol, A)]): Map[Symbol, Set[A]] =
    pairs.groupMap(_._1)(_._2).map { case (owner, targets) => owner -> targets.toSet }

  private final case class FileData(
      file: Path,
      text: String,
      tree: Tree,
      nodes: Vector[DefNode],
      edges: Vector[(Symbol, Symbol)],
      supertypes: Vector[(Symbol, Symbol)],
      instantiations: Vector[(Symbol, Symbol)],
      structuralUses: Vector[(Symbol, Symbol)],
      derivations: Vector[(Symbol, String)],
      factoryTargets: Vector[(Symbol, Symbol)],
      exportRequests: Vector[(Symbol, String, String)],
      reflectiveNames: Vector[(Symbol, String)],
      flags: Vector[(Symbol, DefFlag)],
      macroImplementations: Vector[Symbol]
  )

  def build(
      sourceRoot: Path,
      semanticdbDirs: Vector[Path],
      sourceDirs: Vector[Path],
      rules: ScalaVersionRules
  ): Index = {
    val (unreadable, readable) = semanticdbDirs.flatMap(readSemanticdbDocuments).separate
    val docs = readable.flatten
    val locate = SourceLocator(sourceRoot, sourceDirs)
    val (unusable, facts) = docs
      .mapFilter(doc => locate(doc.uri).map(file => buildFileData(doc = doc, file = file, rules = rules)))
      .separate

    val overrideEdges = for {
      info <- docs.flatMap(_.symbols)
      parent <- info.overriddenSymbols
    } yield Symbol(parent) -> Symbol(info.symbol)

    val (unreadableJava, javaFileData) =
      JavaSources.listJavaFilesUnder(sourceDirs).map(JavaSources.readJavaFile).separate

    val defs = (facts.flatMap(_.nodes) ++ javaFileData.flatMap(_.nodes)).map(node => node.symbol -> node).toMap
    val reflective = resolveReflectiveNames(facts.flatMap(_.reflectiveNames), defs)
    val javaTypes = defs.values.toVector.collect { case node if node.kind === DefKind.JavaType => node.symbol }
    val javaEdges =
      resolveReflectiveNames(javaFileData.flatMap(_.references), defs.filter(_._2.kind === DefKind.JavaType))
    val exportRequests = facts.flatMap(_.exportRequests)
    val underOwnerName = groupMembersByOwnerName(defs)

    Index(
      defs = defs,
      defsByFile = facts.map(f => f.file -> f.nodes).toMap ++ javaFileData.map(f => f.file -> f.nodes).toMap,
      edges = groupByOwner(
        redirectThroughForwarders(
          edges =
            redirectEdgesToJavaTypes(edges = facts.flatMap(_.edges) ++ reflective, defs = defs, javaTypes = javaTypes),
          defs = defs,
          forwarders = buildForwarderMap(exportRequests, underOwnerName)
        ) ++ javaEdges
      ),
      overriddenBy = groupByOwner(overrideEdges),
      overrides = groupByOwner(overrideEdges.map(_.swap)),
      supertypes = groupByOwner(facts.flatMap(_.supertypes)),
      instantiations = groupByOwner(facts.flatMap(_.instantiations)),
      structuralUses = groupByOwner(facts.flatMap(_.structuralUses)),
      trees = facts.map(f => f.file -> f.tree).toMap,
      derivations = groupByOwner(facts.flatMap(_.derivations)),
      factoryTargets = groupByOwner(facts.flatMap(_.factoryTargets)),
      exports = resolveExports(exportRequests, underOwnerName),
      flags = groupByOwner(facts.flatMap(_.flags)),
      macroImplementations = facts.flatMap(_.macroImplementations).toSet,
      reflectiveTargets = reflective.map(_._2).toSet,
      sources = facts.map(f => f.file -> f.text).toMap ++ javaFileData.map(f => f.file -> f.text).toMap,
      warnings =
        rules.checkForMissingSynthetics(docs).toVector ++ (unreadable ++ unusable ++ unreadableJava).map(_.getMessage)
    )
  }

  private def redirectThroughForwarders(
      edges: Vector[(Symbol, Symbol)],
      defs: Map[Symbol, DefNode],
      forwarders: Map[Symbol, Symbol]
  ): Vector[(Symbol, Symbol)] =
    if (forwarders.isEmpty) edges
    else
      edges.map {
        case (from, to) if defs.contains(to) => from -> to
        case (from, to)                      => from -> forwarders.getOrElse(to, to)
      }

  private def redirectEdgesToJavaTypes(
      edges: Vector[(Symbol, Symbol)],
      defs: Map[Symbol, DefNode],
      javaTypes: Vector[Symbol]
  ): Vector[(Symbol, Symbol)] =
    if (javaTypes.isEmpty) edges
    else {
      val longestFirst = javaTypes.sortBy(-_.value.length)
      edges.map { case (from, to) =>
        if (defs.contains(to)) from -> to
        else
          longestFirst.find(javaType => to.value.startsWith(javaType.value)) match {
            case Some(owner) => from -> owner
            case None        => from -> to
          }
      }
    }

  private def collectReferenceOffsets(doc: semanticdb.TextDocument, lines: LineIndex): Map[Int, Symbol] =
    collectOccurrenceOffsets(doc = doc, lines = lines, wanted = _.role.isReference)

  private def collectOccurrenceOffsets(
      doc: semanticdb.TextDocument,
      lines: LineIndex,
      wanted: semanticdb.SymbolOccurrence => Boolean
  ): Map[Int, Symbol] = {
    val offsets = for {
      occurrence <- doc.occurrences.iterator.filter(wanted)
      range <- occurrence.range
    } yield lines.charOffset(range.startLine, range.startCharacter) -> Symbol(occurrence.symbol)

    offsets.toMap
  }

  @tailrec
  private def parseWithDialects(
      uri: String,
      text: String,
      remaining: Vector[Dialect],
      failures: Vector[Parsed.Error]
  ): Either[SliceFailure, Tree] = remaining match {
    case head +: rest =>
      head.apply(Input.VirtualFile(uri, text)).parse[Source].toEither match {
        case Left(error) => parseWithDialects(uri = uri, text = text, remaining = rest, failures = failures :+ error)
        case Right(tree) => Right(tree)
      }
    case _ => Left(SliceFailure(failures.map(_.message).mkString("; ")))
  }

  private def parseSource(uri: String, text: String, rules: ScalaVersionRules): Either[SliceFailure, Tree] =
    parseWithDialects(uri = uri, text = text, remaining = rules.dialects, failures = Vector.empty)

  private def buildFileData(
      doc: semanticdb.TextDocument,
      file: Path,
      rules: ScalaVersionRules
  ): Either[SliceFailure, FileData] =
    readFileText(file).flatMap { text =>
      val lines = LineIndex(text)
      parseSource(uri = doc.uri, text = text, rules = rules)
        .leftMap(message => SliceFailure(s"parse failed for $file: $message"))
        .map { tree =>
          val nodes = collectDefNodes(tree = tree, lines = lines, doc = doc, file = file)
          val enclosing = EnclosingNodes(nodes)
          val referenceAt = collectReferenceOffsets(doc, lines)
          val symbolAtStart = nodes.map(node => node.start -> node.symbol).toMap
          val (instantiated, structural) =
            collectInstantiations(tree = tree, referenceAt = referenceAt, enclosing = enclosing)
          val (derived, fixed) = collectClassShapes(tree, symbolAtStart)
          FileData(
            file = file,
            text = text,
            tree = tree,
            nodes = nodes,
            edges = collectEdges(doc = doc, lines = lines, enclosing = enclosing) ++
              collectSyntheticEdges(doc = doc, lines = lines, enclosing = enclosing),
            supertypes = collectSupertypes(tree = tree, referenceAt = referenceAt, symbolAtStart = symbolAtStart),
            instantiations = instantiated,
            structuralUses = structural,
            derivations = derived,
            factoryTargets = collectFactoryCalls(tree = tree, referenceAt = referenceAt, enclosing = enclosing),
            exportRequests = collectExports(tree, enclosing),
            reflectiveNames = collectReflectiveNames(tree = tree, enclosing = enclosing),
            flags = collectDefFlags(
              conversions = rules.collectConversions(tree, symbolAtStart),
              fixed = fixed,
              opaque = collectOpaqueBodies(tree, symbolAtStart)
            ),
            macroImplementations = collectMacroImplementations(tree = tree, referenceAt = referenceAt)
          )
        }
    }

  private def collectDefFlags(
      conversions: Vector[Symbol],
      fixed: Vector[Symbol],
      opaque: Vector[Symbol]
  ): Vector[(Symbol, DefFlag)] =
    conversions.map(_ -> DefFlag.ConversionGiven) ++
      fixed.map(_ -> DefFlag.FixedConstructorParameters) ++
      opaque.map(_ -> DefFlag.OpaqueBody)

  private def readFileText(file: Path): Either[SliceFailure, String] = {
    Either
      .catchNonFatal {
        String(Files.readAllBytes(file), StandardCharsets.UTF_8)
      }
      .leftMap(failure => SliceFailure(s"could not read $file", failure))
  }

  private def readSemanticdbDocuments(dir: Path): Vector[Either[SliceFailure, Vector[semanticdb.TextDocument]]] =
    SourceFiles.listSourceFilesUnder(dir).collect {
      case file if file.getFileName.toString.endsWith(".semanticdb") =>
        Either
          .catchNonFatal { semanticdb.TextDocuments.parseFrom(Files.readAllBytes(file)).documents.toVector }
          .leftMap(failure => SliceFailure(s"could not read $file", failure))
    }

  private def collectDefinitionOffsets(doc: semanticdb.TextDocument, lines: LineIndex): Map[Int, Symbol] =
    collectOccurrenceOffsets(doc = doc, lines = lines, wanted = _.role.isDefinition)

  private def collectDefNodes(tree: Tree, lines: LineIndex, doc: semanticdb.TextDocument, file: Path): Vector[DefNode] =
    collectDefinitionsInTree(t = tree, owner = None, symbolAt = collectDefinitionOffsets(doc, lines), file = file)

  private def isExpandedAtCallSite(t: Tree): Boolean = t match {
    case d: Defn.Def   => d.mods.exists(_.is[Mod.Inline])
    case _: Defn.Macro => true
    case _             => false
  }

  private def isAbstractDeclaration(t: Tree): Boolean = t match {
    case _: Decl.Def | _: Decl.Val | _: Decl.Var | _: Decl.Type | _: Decl.Given => true
    case _                                                                      => false
  }

  private def definitionKindAndName(t: Tree): Option[(DefKind, Position)] = t match {
    case d: Defn.Def        => Some((DefKind.Def, d.name.pos))
    case d: Defn.Macro      => Some((DefKind.Def, d.name.pos))
    case d: Decl.Def        => Some((DefKind.Def, d.name.pos))
    case d: Defn.Val        => soleBoundName(d.pats).map(name => (DefKind.Val, name.pos))
    case d: Decl.Val        => soleBoundName(d.pats).map(name => (DefKind.Val, name.pos))
    case d: Defn.Var        => soleBoundName(d.pats).map(name => (DefKind.Var, name.pos))
    case d: Decl.Var        => soleBoundName(d.pats).map(name => (DefKind.Var, name.pos))
    case c: Ctor.Secondary  => Some((DefKind.Def, c.name.pos))
    case d: Defn.Class      => Some((DefKind.Class, d.name.pos))
    case d: Defn.Trait      => Some((DefKind.Trait, d.name.pos))
    case d: Defn.Object     => Some((DefKind.Object, d.name.pos))
    case d: Defn.Type       => Some((DefKind.Type, d.name.pos))
    case d: Pkg.Object      => Some((DefKind.Object, d.name.pos))
    case d: Defn.Given      => Some((DefKind.Given, d.name.pos))
    case d: Defn.GivenAlias => Some((DefKind.Given, d.name.pos))
    case d: Decl.Given      => Some((DefKind.Given, d.name.pos))
    case d: Defn.Enum       => Some((DefKind.Enum, d.name.pos))
    case d: Defn.EnumCase   => Some((DefKind.EnumCase, d.name.pos))
    case _                  => None
  }

  private def soleBoundName(pats: List[Pat]): Option[Pat.Var] = pats match {
    case (only: Pat.Var) :: Nil => Some(only)
    case _                      => None
  }

  private def boundDefinitionKind(t: Tree): Option[DefKind] = t match {
    case _: Defn.Var | _: Decl.Var => Some(DefKind.Var)
    case _: Defn.Val | _: Decl.Val => Some(DefKind.Val)
    case _: Defn.RepeatedEnumCase  => Some(DefKind.EnumCase)
    case _                         => None
  }

  private def boundNameTrees(t: Tree): Vector[Name] = t match {
    case d: Defn.Val              => collectPatternNames(d.pats)
    case d: Decl.Val              => collectPatternNames(d.pats)
    case d: Defn.Var              => collectPatternNames(d.pats)
    case d: Decl.Var              => collectPatternNames(d.pats)
    case d: Defn.RepeatedEnumCase => d.cases.toVector
    case _                        => Vector.empty
  }

  private def collectPatternNames(pats: List[Pat]): Vector[Name] =
    pats.toVector.flatMap(pat => pat.collect { case bound: Pat.Var => bound.name })

  private def toBindingGroupNode(
      t: Tree,
      owner: Option[Symbol],
      symbolAt: Map[Int, Symbol],
      file: Path
  ): Option[DefNode] = {
    val names = boundNameTrees(t)
    val bound = names.flatMap(name => symbolAt.get(name.pos.start).filterNot(_.isLocalSymbol))
    boundDefinitionKind(t)
      .filter(_ => bound.nonEmpty && (names.size > 1 || soleBoundName(declaredPatterns(t)).isEmpty))
      .map(kind =>
        DefNode(
          Symbol.synthetic(s"binding-group:$file:${t.pos.start}"),
          kind,
          names.head.pos.text,
          file,
          t.pos.start,
          t.pos.end,
          owner,
          isAbstract = isAbstractDeclaration(t),
          expandsAtCallSite = false
        )
      )
  }

  private def declaredPatterns(t: Tree): List[Pat] = t match {
    case d: Defn.Val => d.pats
    case d: Decl.Val => d.pats
    case d: Defn.Var => d.pats
    case d: Decl.Var => d.pats
    case _           => Nil
  }

  private def toBoundNameNodes(t: Tree, groupSymbol: Symbol, symbolAt: Map[Int, Symbol], file: Path): Vector[DefNode] =
    boundNameTrees(t).flatMap(name =>
      symbolAt.get(name.pos.start).filterNot(_.isLocalSymbol).map { sym =>
        DefNode(
          sym,
          DefKind.Binding,
          name.pos.text,
          file,
          name.pos.start,
          name.pos.end,
          Some(groupSymbol),
          isAbstract = isAbstractDeclaration(t),
          expandsAtCallSite = false
        )
      }
    )

  private def toConstructorParamNodes(
      t: Tree,
      classSymbol: Symbol,
      symbolAt: Map[Int, Symbol],
      file: Path
  ): Vector[DefNode] = t match {
    case d: Defn.Class =>
      for {
        clause <- d.ctor.paramClauses.toVector
        param <- clause.values
        sym <- symbolAt.get(param.name.pos.start).filterNot(_.isLocalSymbol)
      } yield DefNode(
        sym,
        DefKind.Param,
        param.name.value,
        file,
        param.pos.start,
        param.pos.end,
        Some(classSymbol),
        isAbstract = false,
        expandsAtCallSite = false
      )
    case _ => Vector.empty
  }

  private def toExtensionGroupNode(t: Tree, owner: Option[Symbol], file: Path): Option[DefNode] = t match {
    case g: Defn.ExtensionGroup =>
      Some(
        DefNode(
          Symbol.synthetic(s"extension-group:$file:${g.pos.start}"),
          DefKind.Extension,
          "extension",
          file,
          g.pos.start,
          g.pos.end,
          owner,
          isAbstract = false,
          expandsAtCallSite = false
        )
      )
    case _ => None
  }

  private def toAnonymousGivenNode(
      t: Tree,
      owner: Option[Symbol],
      symbolAt: Map[Int, Symbol],
      file: Path
  ): Option[DefNode] = t match {
    case g: Defn.Given =>
      symbolAt.toVector
        .collect {
          case (offset, sym) if offset > g.pos.start && offset < g.pos.end && !sym.isLocalSymbol => sym
        }
        .mapFilter(_.findOwnerSymbol)
        .minByOption(_.value.length)
        .map(sym =>
          DefNode(
            sym,
            DefKind.Given,
            sym.toDisplayName,
            file,
            g.pos.start,
            g.pos.end,
            owner,
            isAbstract = false,
            expandsAtCallSite = false
          )
        )
    case _ => None
  }

  private def collectDefinitionsInTree(
      t: Tree,
      owner: Option[Symbol],
      symbolAt: Map[Int, Symbol],
      file: Path
  ): Vector[DefNode] = if (t.is[Type.Refine]) Vector.empty
  else {
    val here = definitionKindAndName(t)
      .flatMap { case (kind, pos) =>
        symbolAt.get(pos.start).collect {
          case sym if !sym.isLocalSymbol =>
            DefNode(
              sym,
              kind,
              pos.text,
              file,
              t.pos.start,
              t.pos.end,
              owner,
              isAbstractDeclaration(t),
              isExpandedAtCallSite(t)
            )
        }
      }
      .orElse(toAnonymousGivenNode(t = t, owner = owner, symbolAt = symbolAt, file = file))
    val group =
      if (here.nonEmpty) None
      else
        toExtensionGroupNode(t = t, owner = owner, file = file)
          .orElse(toBindingGroupNode(t = t, owner = owner, symbolAt = symbolAt, file = file))
    val nextOwner = here.map(_.symbol).orElse(group.map(_.symbol)).orElse(owner)

    here.toVector ++
      group.toVector ++
      here.toVector.flatMap(n =>
        toConstructorParamNodes(t = t, classSymbol = n.symbol, symbolAt = symbolAt, file = file)
      ) ++
      group.toVector.flatMap(g => toBoundNameNodes(t = t, groupSymbol = g.symbol, symbolAt = symbolAt, file = file)) ++
      t.children.toVector.flatMap(child =>
        collectDefinitionsInTree(t = child, owner = nextOwner, symbolAt = symbolAt, file = file)
      )
  }

  private def collectSyntheticEdges(
      doc: semanticdb.TextDocument,
      lines: LineIndex,
      enclosing: EnclosingNodes
  ): Vector[(Symbol, Symbol)] = {
    val referenced = for {
      synthetic <- doc.synthetics.toVector
      range <- synthetic.range.toVector
      symbol <- collectSyntheticTreeSymbols(synthetic.tree).map(Symbol.apply).filterNot(_.isLocalSymbol)
    } yield lines.charOffset(range.startLine, range.startCharacter) -> symbol

    dropSelfEdges(enclosing.attributeToOwners(referenced))
  }

  private def dropSelfEdges(edges: Vector[(Symbol, Symbol)]): Vector[(Symbol, Symbol)] =
    edges.filterNot { case (owner, referenced) => owner === referenced }

  private def collectSyntheticTreeSymbols(tree: semanticdb.Tree): Vector[String] = tree match {
    case id: semanticdb.IdTree => Vector(id.symbol)
    case sel: semanticdb.SelectTree =>
      collectSyntheticTreeSymbols(sel.qualifier) ++ sel.id.toVector.flatMap(collectSyntheticTreeSymbols)
    case a: semanticdb.ApplyTree =>
      collectSyntheticTreeSymbols(a.function) ++ a.arguments.toVector.flatMap(collectSyntheticTreeSymbols)
    case ta: semanticdb.TypeApplyTree => collectSyntheticTreeSymbols(ta.function)
    case f: semanticdb.FunctionTree =>
      f.parameters.toVector.flatMap(collectSyntheticTreeSymbols) ++ collectSyntheticTreeSymbols(f.body)
    case m: semanticdb.MacroExpansionTree => collectSyntheticTreeSymbols(m.beforeExpansion)
    case _                                => Vector.empty
  }

  private def collectInstantiations(
      tree: Tree,
      referenceAt: Map[Int, Symbol],
      enclosing: EnclosingNodes
  ): (Vector[(Symbol, Symbol)], Vector[(Symbol, Symbol)]) = {
    val constructions = tree
      .collect {
        case init: Init         => resolveTypeReference(referenceAt = referenceAt, at = init.pos.start, tpe = init.tpe)
        case d: Defn.GivenAlias => resolveTypeReference(referenceAt = referenceAt, at = d.pos.start, tpe = d.decltpe)
      }
      .flatten
      .toVector

    val inlineImplementations = tree
      .collect {
        case anon: Term.NewAnonymous =>
          anon.templ.inits.flatMap(init =>
            resolveTypeReference(referenceAt = referenceAt, at = anon.pos.start, tpe = init.tpe)
          )
        case d: Defn.GivenAlias if !d.body.is[Term.New] =>
          resolveTypeReference(referenceAt = referenceAt, at = d.pos.start, tpe = d.decltpe)
        case d: Defn.Val if isLambda(d.rhs) =>
          d.decltpe.flatMap(tpe => resolveTypeReference(referenceAt = referenceAt, at = d.pos.start, tpe = tpe))
        case d: Defn.Def if isLambda(d.body) =>
          d.decltpe.flatMap(tpe => resolveTypeReference(referenceAt = referenceAt, at = d.pos.start, tpe = tpe))
      }
      .flatten
      .toVector

    (enclosing.attributeToOwners(constructions), enclosing.attributeToOwners(inlineImplementations))
  }

  private def collectSupertypes(
      tree: Tree,
      referenceAt: Map[Int, Symbol],
      symbolAtStart: Map[Int, Symbol]
  ): Vector[(Symbol, Symbol)] =
    tree
      .collect {
        case d: Defn.Class =>
          collectDeclaredSupertypes(
            referenceAt = referenceAt,
            symbolAtStart = symbolAtStart,
            at = d.pos,
            templ = d.templ
          )
        case d: Defn.Trait =>
          collectDeclaredSupertypes(
            referenceAt = referenceAt,
            symbolAtStart = symbolAtStart,
            at = d.pos,
            templ = d.templ
          )
        case d: Defn.Object =>
          collectDeclaredSupertypes(
            referenceAt = referenceAt,
            symbolAtStart = symbolAtStart,
            at = d.pos,
            templ = d.templ
          )
        case d: Defn.Enum =>
          collectDeclaredSupertypes(
            referenceAt = referenceAt,
            symbolAtStart = symbolAtStart,
            at = d.pos,
            templ = d.templ
          )
      }
      .flatten
      .toVector

  private def collectDeclaredSupertypes(
      referenceAt: Map[Int, Symbol],
      symbolAtStart: Map[Int, Symbol],
      at: Position,
      templ: Template
  ): Vector[(Symbol, Symbol)] =
    symbolAtStart.get(at.start) match {
      case Some(symbol) =>
        val declared = templ.inits.toVector.map(_.tpe) ++ templ.body.selfOpt.flatMap(_.decltpe).toVector
        declared.flatMap(tpe => referenceAt.get(typeNameStartOffset(tpe))).map(parent => symbol -> parent)
      case None => Vector.empty
    }

  private def typeNameStartOffset(tpe: Type): Int = tpe match {
    case Type.Apply.After_4_6_0(t, _) => t.pos.start
    case other                        => other.pos.start
  }

  @tailrec
  private def isLambda(body: Tree): Boolean = body match {
    case _: Term.Function | _: Term.AnonymousFunction | _: Term.PartialFunction => true
    case Term.Block(List(single))                                               => isLambda(single)
    case _                                                                      => false
  }

  private def resolveTypeReference(referenceAt: Map[Int, Symbol], at: Int, tpe: Type): Option[(Int, Symbol)] =
    referenceAt.get(typeNameStartOffset(tpe)).map(sym => (at, sym))

  private def extendsAnyVal(templ: Template): Boolean =
    templ.inits.exists(init => Symbol.takeLastSegment(init.tpe.syntax) === "AnyVal")

  private final case class ClassShape(symbol: Symbol, derives: List[Type], fixedByModifiers: Boolean)

  private def toClassShape(
      symbolAtStart: Map[Int, Symbol],
      templ: Template,
      at: Position,
      fixedByModifiers: Boolean
  ): Option[ClassShape] =
    symbolAtStart
      .get(at.start)
      .map(symbol => ClassShape(symbol = symbol, derives = templ.derives, fixedByModifiers = fixedByModifiers))

  private def collectClassShapes(
      tree: Tree,
      symbolAtStart: Map[Int, Symbol]
  ): (Vector[(Symbol, String)], Vector[Symbol]) = {
    val shapes = tree.collect {
      case d: Defn.Class =>
        toClassShape(
          symbolAtStart = symbolAtStart,
          templ = d.templ,
          at = d.pos,
          fixedByModifiers = extendsAnyVal(d.templ) ||
            d.mods.exists(_.is[Mod.Implicit]) ||
            d.templ.body.stats.exists(_.is[Ctor.Secondary])
        )
      case d: Defn.Enum =>
        toClassShape(symbolAtStart = symbolAtStart, templ = d.templ, at = d.pos, fixedByModifiers = false)
      case d: Defn.Trait =>
        toClassShape(symbolAtStart = symbolAtStart, templ = d.templ, at = d.pos, fixedByModifiers = false)
      case d: Defn.Object =>
        toClassShape(symbolAtStart = symbolAtStart, templ = d.templ, at = d.pos, fixedByModifiers = false)
    }.flatten

    val derived = for {
      shape <- shapes.toVector
      tpe <- shape.derives
      name = tpe match {
        case Type.Apply.After_4_6_0(inner, _) => inner.syntax
        case other                            => other.syntax
      }
    } yield shape.symbol -> Symbol.takeLastSegment(name)

    val fixed = shapes.toVector.collect {
      case shape if shape.fixedByModifiers || shape.derives.nonEmpty => shape.symbol
    }

    (derived, fixed)
  }

  private def findOpaqueBodySymbol(
      symbolAtStart: Map[Int, Symbol],
      indexed: Set[Int],
      pos: Position,
      templ: Template
  ): Option[Symbol] = {
    if (templ.inits.nonEmpty || templ.body.stats.exists(st => !indexed.contains(st.pos.start)))
      symbolAtStart.get(pos.start)
    else None
  }

  private def collectOpaqueBodies(tree: Tree, symbolAtStart: Map[Int, Symbol]): Vector[Symbol] = {
    val indexed = symbolAtStart.keySet

    tree
      .collect {
        case d: Defn.Object =>
          findOpaqueBodySymbol(symbolAtStart = symbolAtStart, indexed = indexed, pos = d.pos, templ = d.templ)
        case d: Pkg.Object =>
          findOpaqueBodySymbol(symbolAtStart = symbolAtStart, indexed = indexed, pos = d.pos, templ = d.templ)
      }
      .flatten
      .toVector
  }

  private def collectFactoryCalls(
      tree: Tree,
      referenceAt: Map[Int, Symbol],
      enclosing: EnclosingNodes
  ): Vector[(Symbol, Symbol)] = {
    val applied = tree
      .collect {
        case Term.Apply.After_4_6_0(fn, _) => referenceAt.get(calleeNameStartOffset(fn)).map(sym => (fn.pos.start, sym))
        case Pat.Extract.After_4_6_0(fn, _) =>
          referenceAt.get(calleeNameStartOffset(fn)).map(sym => (fn.pos.start, sym))
      }
      .flatten
      .toVector

    enclosing.attributeToOwners(applied)
  }

  private def calleeNameStartOffset(t: Tree): Int = t match {
    case Term.Select(_, name) => name.pos.start
    case Type.Select(_, name) => name.pos.start
    case other                => other.pos.start
  }

  private def collectExports(tree: Tree, enclosing: EnclosingNodes): Vector[(Symbol, String, String)] = {
    val requested = tree
      .collect { case e: Export =>
        for {
          importer <- e.importers.toVector
          prefix = Symbol.takeLastSegment(importer.ref.syntax)
          named <- importer.importees.collect {
            case Importee.Name(n)      => n.value
            case Importee.Rename(n, _) => n.value
            case _: Importee.Wildcard  => everyMember
          }
        } yield e.pos.start -> (prefix, named)
      }
      .flatten
      .toVector

    enclosing.attributeToOwners(requested).map { case (owner, (prefix, named)) => (owner, prefix, named) }
  }

  private val everyMember = "*"

  private def groupMembersByOwnerName(defs: Map[Symbol, DefNode]): Map[String, Vector[DefNode]] =
    defs.values.toVector.mapFilter(node => node.owner.flatMap(defs.get).map(_.displayName -> node)).groupMap(_._1)(_._2)

  private def findExportedMembers(
      underOwnerName: Map[String, Vector[DefNode]],
      owner: Symbol,
      prefix: String,
      member: String
  ): Vector[DefNode] =
    underOwnerName
      .getOrElse(prefix, Vector.empty)
      .filter(node => (member === everyMember || node.displayName === member) && !node.owner.contains(owner))

  private def buildForwarderMap(
      requests: Vector[(Symbol, String, String)],
      underOwnerName: Map[String, Vector[DefNode]]
  ): Map[Symbol, Symbol] =
    requests.flatMap { case (owner, prefix, member) =>
      findExportedMembers(underOwnerName = underOwnerName, owner = owner, prefix = prefix, member = member)
        .mapFilter(node =>
          node.symbol.findOwnerSymbol
            .map(exporting => Symbol(owner.value + node.symbol.value.stripPrefix(exporting.value)) -> node.symbol)
        )
    }.toMap

  private def resolveExports(
      requests: Vector[(Symbol, String, String)],
      underOwnerName: Map[String, Vector[DefNode]]
  ): Map[Symbol, Set[Symbol]] =
    requests.foldMap { case (owner, prefix, member) =>
      val targets = underOwnerName
        .getOrElse(prefix, Vector.empty)
        .collect { case node if node.displayName === member => node.symbol }
        .toSet
      if (targets.isEmpty) Map.empty[Symbol, Set[Symbol]] else Map(owner -> targets)
    }

  private def collectReflectiveNames(tree: Tree, enclosing: EnclosingNodes): Vector[(Symbol, String)] = {
    val named = tree.collect {
      case literal: Lit.String if literal.value.contains('.') => literal.pos.start -> literal.value
    }.toVector

    enclosing.attributeToOwners(named)
  }

  private def resolveReflectiveNames(
      requests: Vector[(Symbol, String)],
      defs: Map[Symbol, DefNode]
  ): Vector[(Symbol, Symbol)] = {
    val byDottedName = defs.values.toVector.groupMap(_.dottedName)(_.symbol)
    for {
      (owner, name) <- requests
      named <- byDottedName.getOrElse(name, Vector.empty).filterNot(_ === owner)
    } yield owner -> named
  }

  private def collectMacroImplementations(tree: Tree, referenceAt: Map[Int, Symbol]): Vector[Symbol] =
    tree
      .collect { case d: Defn.Macro =>
        referenceAt.collect {
          case (offset, symbol) if offset >= d.body.pos.start && offset < d.body.pos.end => symbol
        }
      }
      .flatten
      .toVector
      .filterNot(_.isLocalSymbol)

  private def collectEdges(
      doc: semanticdb.TextDocument,
      lines: LineIndex,
      enclosing: EnclosingNodes
  ): Vector[(Symbol, Symbol)] = {
    val referenced = for {
      occurrence <- doc.occurrences.toVector.filter(o => o.role.isReference && !Symbol(o.symbol).isLocalSymbol)
      range <- occurrence.range.toVector
    } yield lines.charOffset(range.startLine, range.startCharacter) -> Symbol(occurrence.symbol)

    dropSelfEdges(enclosing.attributeToOwners(referenced))
  }
}
