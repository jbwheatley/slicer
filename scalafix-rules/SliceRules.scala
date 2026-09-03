import scalafix.v1._

import scala.meta._

class RequireOverride extends SemanticRule("RequireOverride") {

  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect {
      case definition: Defn.Def if lacksOverride(definition.mods, definition.symbol)  => addOverride(definition)
      case definition: Defn.Val if lacksOverride(definition.mods, definition.symbol)  => addOverride(definition)
      case definition: Defn.Var if lacksOverride(definition.mods, definition.symbol)  => addOverride(definition)
      case definition: Defn.Type if lacksOverride(definition.mods, definition.symbol) => addOverride(definition)
    }.asPatch

  private def lacksOverride(mods: List[Mod], symbol: Symbol)(implicit doc: SemanticDocument): Boolean =
    !mods.exists(_.is[Mod.Override]) && overridesSomething(symbol)

  private def overridesSomething(symbol: Symbol)(implicit doc: SemanticDocument): Boolean =
    symbol.info.exists(_.overriddenSymbols.nonEmpty)

  private def addOverride(definition: Defn): Patch =
    definition.tokens
      .find {
        case _: Token.KwDef | _: Token.KwVal | _: Token.KwVar | _: Token.KwType => true
        case _                                                                  => false
      }
      .map(keyword => Patch.addLeft(keyword, "override "))
      .getOrElse(Patch.empty)
}

class NoNestedDefs extends SyntacticRule("NoNestedDefs") {

  override def fix(implicit doc: SyntacticDocument): Patch =
    doc.tree.collect {
      case definition: Defn.Def if isNested(definition) => Patch.lint(NestedDef(definition))
    }.asPatch

  private def isNested(definition: Defn.Def): Boolean =
    enclosingScope(definition.parent).exists {
      case _: Defn.Def | _: Defn.Val | _: Defn.Var | _: Defn.GivenAlias => true
      case _                                                            => false
    }

  private def enclosingScope(tree: Option[Tree]): Option[Tree] = tree match {
    case Some(_: Template) | Some(_: Source) | Some(_: Pkg) | None => None
    case Some(found: Defn.Def)                                     => Some(found)
    case Some(found: Defn.Val)                                     => Some(found)
    case Some(found: Defn.Var)                                     => Some(found)
    case Some(found: Defn.GivenAlias)                              => Some(found)
    case Some(other)                                               => enclosingScope(other.parent)
  }
}

final case class NestedDef(definition: Defn.Def) extends Diagnostic {

  override def position: Position = definition.name.pos

  override def message: String =
    s"`${definition.name.value}` is defined inside another definition. " +
      "Hoist it to a member of the enclosing object, class or trait, passing what it closed over as parameters."
}

class NoForComprehensionGuards extends SyntacticRule("NoForComprehensionGuards") {

  override def fix(implicit doc: SyntacticDocument): Patch =
    doc.tree.collect { case guard: Enumerator.Guard => Patch.lint(ForComprehensionGuard(guard)) }.asPatch
}

final case class ForComprehensionGuard(guard: Enumerator.Guard) extends Diagnostic {

  override def position: Position = guard.pos

  override def message: String =
    s"`if ${guard.cond.syntax}` guards a for comprehension. " +
      "Filter the generator it narrows instead, with .filter, .filterNot or .collect."
}

class NoChainedFlatMaps extends SyntacticRule("NoChainedFlatMaps") {

  override def fix(implicit doc: SyntacticDocument): Patch =
    doc.tree.collect {
      case call: Term.Apply if isFlatMap(call) && reachesAnotherFlatMap(call) => Patch.lint(ChainedFlatMap(call))
    }.asPatch

  private def isFlatMap(tree: Tree): Boolean = tree match {
    case Term.Apply.After_4_6_0(Term.Select(_, Term.Name("flatMap")), _) => true
    case _                                                               => false
  }

  private def reachesAnotherFlatMap(call: Term.Apply): Boolean =
    receiverOf(call).exists(isFlatMap) ||
      call.argClause.values.exists(argument => argument.collect { case inner if isFlatMap(inner) => inner }.nonEmpty)

  private def receiverOf(call: Term.Apply): Option[Term] = call.fun match {
    case Term.Select(qualifier, _) => Some(qualifier)
    case _                         => None
  }
}

final case class ChainedFlatMap(call: Term.Apply) extends Diagnostic {

  override def position: Position = call.fun.pos

  override def message: String =
    "This `flatMap` chains or nests another one. Write the whole chain as a single for comprehension."
}
