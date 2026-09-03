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

import scala.annotation.tailrec
import scala.collection.immutable.Queue

import slicer.model.*

import cats.Eq
import cats.syntax.eq.*

private[slicer] object Reachability {

  private enum Reach {
    case Referenced, Inherited
  }

  private object Reach {
    given Eq[Reach] = Eq.fromUniversalEquals
  }

  private final case class Step(symbol: Symbol, reach: Reach)

  private final case class Walk(
      index: Index,
      options: SliceOptions,
      kept: Set[Symbol],
      processed: Map[Symbol, Reach],
      choices: Map[Symbol, Set[Symbol]],
      queue: Queue[Step]
  ) {

    private def enqueue(next: Symbol): Walk = enqueueAll(Vector(next))

    def enqueueAll(next: Iterable[Symbol]): Walk =
      copy(queue = queue.enqueueAll(next.map(Step(_, Reach.Referenced))))

    def enqueueInherited(next: Iterable[Symbol]): Walk =
      copy(queue = queue.enqueueAll(next.map(Step(_, Reach.Inherited))))

    def isProcessed(step: Step): Boolean =
      processed.get(step.symbol) match {
        case Some(Reach.Referenced) => true
        case Some(Reach.Inherited)  => step.reach === Reach.Inherited
        case None                   => false
      }

    def recordProcessed(step: Step): Walk = copy(processed = processed.updated(step.symbol, step.reach))

    private def keepSymbol(symbol: Symbol): Walk = copy(kept = kept + symbol)

    private def recordChoices(symbol: Symbol, implementations: Set[Symbol]): Walk =
      if (implementations.size <= 1) this
      else copy(choices = choices.updated(symbol, choices.getOrElse(symbol, Set.empty) ++ implementations))

    private def findConstructedCompanionClass(symbol: Symbol): Option[Symbol] =
      symbol.findCompanionClass.filter(index.constructorParams.contains)

    private def keepConstructorEffects(target: Symbol): Walk =
      target.findParameterFixingOwner.orElse(findConstructedCompanionClass(target)) match {
        case Some(cls) => enqueueAll(index.constructorParams.getOrElse(cls, Vector.empty).toSet + cls)
        case None      => this
      }

    private def keepDerivedTypeClasses(symbol: Symbol): Walk =
      index.derivations.getOrElse(symbol, Set.empty).foldLeft(this) { (reached, typeClassName) =>
        index.byDisplayName.getOrElse(typeClassName, Vector.empty).foldLeft(reached) { (reached, typeClass) =>
          reached
            .enqueue(typeClass.symbol)
            .enqueueAll(
              index.membersByOwner
                .getOrElse(typeClass.symbol, Set.empty)
                .collect {
                  case m if GeneratedMember.isDerivation(m.displayName) || m.kind === DefKind.Given => m.symbol
                }
            )
        }
      }

    private def keepGivensInScope(node: DefNode): Walk =
      if (!node.expandsAtCallSite) this
      else {
        val scopes = node.owner.toSet.flatMap { (owner: Symbol) =>
          index.instantiations.getOrElse(owner, Set.empty) ++ index.structuralUses.getOrElse(owner, Set.empty) + owner
        }
        scopes.foldLeft(this) { (reached, scope) =>
          reached.enqueueAll(index.membersByOwner.getOrElse(scope, Set.empty).collect {
            case m if m.kind === DefKind.Given => m.symbol
          })
        }
      }

    private def keepReflectivelyNamedMembers(symbol: Symbol): Walk =
      if (!index.reflectiveTargets.contains(symbol)) this
      else enqueueAll(index.membersByOwner.getOrElse(symbol, Set.empty).map(_.symbol))

    private def keepConstructors(node: DefNode): Walk =
      if (!node.isContainer) this
      else
        enqueueAll(index.membersByOwner.getOrElse(node.symbol, Set.empty).collect {
          case member if member.symbol.isConstructorSymbol => member.symbol
        })

    private def keepFactoryMembers(symbol: Symbol): Walk =
      index.factoryTargets.getOrElse(symbol, Set.empty).foldLeft(this) { (reached, obj) =>
        reached.enqueueAll(
          index.membersByOwner.getOrElse(obj, Set.empty).collect {
            case m if GeneratedMember.isFactoryEntry(m.displayName) => m.symbol
          }
        )
      }

    private def keepExports(symbol: Symbol): Walk = enqueueAll(index.exports.getOrElse(symbol, Set.empty))

    private def keepTypeMembers(node: DefNode): Walk =
      if (node.isContainer)
        enqueueAll(index.membersByOwner.getOrElse(node.symbol, Set.empty).collect {
          case m if m.kind === DefKind.Type && index.overrides.getOrElse(m.symbol, Set.empty).nonEmpty => m.symbol
        })
      else this

    private def keepEnumCases(node: DefNode): Walk =
      if (node.kind === DefKind.Enum)
        enqueueAll(index.membersByOwner.getOrElse(node.symbol, Set.empty).collect {
          case m if m.kind === DefKind.EnumCase => m.symbol
        })
      else this

    private def keepFixedConstructorParameters(symbol: Symbol): Walk =
      if (index.isFlagged(symbol, DefFlag.FixedConstructorParameters))
        enqueueAll(index.constructorParams.getOrElse(symbol, Vector.empty))
      else this

    private def keepInstantiatedClasses(symbol: Symbol): Walk =
      index.instantiations.getOrElse(symbol, Set.empty).foldLeft(this) { (reached, cls) =>
        reached.enqueue(cls).enqueueAll(index.constructorParams.getOrElse(cls, Vector.empty))
      }

    private def keepInlineImplementations(symbol: Symbol): Walk =
      index.structuralUses.getOrElse(symbol, Set.empty).foldLeft(this) { (reached, cls) =>
        reached
          .enqueue(cls)
          .enqueueAll(index.constructorParams.getOrElse(cls, Vector.empty))
          .enqueueAll(index.membersByOwner.getOrElse(cls, Set.empty).collect { case m if m.isAbstract => m.symbol })
      }

    private def keepInheritedBehaviour(symbol: Symbol): Walk =
      index.supertypes.getOrElse(symbol, Set.empty).foldLeft(this) { (reached, parent) =>
        reached.enqueueInherited(
          index.membersByOwner.getOrElse(parent, Set.empty).collect { case m if !m.isAbstract => m.symbol } + parent
        )
      }

    private def keepOwner(node: DefNode, reach: Reach): Walk =
      reach match {
        case Reach.Referenced => enqueueAll(node.owner)
        case Reach.Inherited  => enqueueInherited(node.owner)
      }

    private def keepOverridden(symbol: Symbol): Walk = enqueueInherited(index.overrides.getOrElse(symbol, Set.empty))

    private def keepImplementations(step: Step): Walk =
      if (!options.followImplementations || step.reach === Reach.Inherited) this
      else {
        val implementations = index.overriddenBy.getOrElse(step.symbol, Set.empty)
        recordChoices(symbol = step.symbol, implementations = implementations).enqueueAll(implementations)
      }

    private def keepExternalImplementations(node: DefNode): Walk =
      if (!node.isContainer && node.kind =!= DefKind.Given) this
      else
        enqueueAll(
          index.membersByOwner
            .getOrElse(node.symbol, Set.empty)
            .collect {
              case member
                  if index.overrides
                    .getOrElse(member.symbol, Set.empty)
                    .exists(parent => !index.defs.contains(parent) && !parent.isUniversalMember) =>
                member.symbol
            }
        )

    private def keepInitialisedFields(node: DefNode): Walk =
      if (!options.keepFields || !node.isContainer) this
      else
        enqueueAll(
          index.membersByOwner.getOrElse(node.symbol, Set.empty).collect {
            case m if m.kind === DefKind.Val || m.kind === DefKind.Var => m.symbol
          }
        )

    def keepReachableConversions: Walk = {
      val touchedPackages = kept.map(_.toPackagePrefix)
      enqueueAll(
        index
          .symbolsWithFlag(DefFlag.ConversionGiven)
          .filter(conversion => touchedPackages.contains(conversion.toPackagePrefix))
      )
    }

    def unimplementedMembers: Vector[Symbol] =
      kept.toVector.flatMap { owner =>
        index.membersByOwner.getOrElse(owner, Set.empty).collect {
          case m if !kept(m.symbol) && index.overrides.getOrElse(m.symbol, Set.empty).exists(kept) => m.symbol
        }
      }

    def followStep(step: Step): Walk = {
      val symbol = step.symbol
      val parents = (index.overrides.getOrElse(symbol, Set.empty) ++ index.supertypes.getOrElse(symbol, Set.empty))
        .flatMap(_.withCompanionSymbol)
      val reachable = index.edges
        .getOrElse(symbol, Set.empty)
        .flatMap(target => target.withCompanionSymbol ++ target.findGetterForSetter)
      val (inherited, referenced) = step.reach match {
        case Reach.Inherited  => (reachable, Set.empty[Symbol])
        case Reach.Referenced => reachable.partition(parents.contains)
      }
      val targets = inherited ++ referenced
      val constructed = targets.foldLeft(this)((reached, target) => reached.keepConstructorEffects(target))

      index.defs.get(symbol) match {
        case None => constructed
        case Some(node) =>
          constructed
            .keepSymbol(symbol)
            .keepDerivedTypeClasses(symbol)
            .keepGivensInScope(node)
            .keepFactoryMembers(symbol)
            .keepReflectivelyNamedMembers(symbol)
            .keepConstructors(node)
            .keepExports(symbol)
            .keepTypeMembers(node)
            .keepEnumCases(node)
            .keepFixedConstructorParameters(symbol)
            .keepInstantiatedClasses(symbol)
            .keepInlineImplementations(symbol)
            .keepInheritedBehaviour(symbol)
            .keepOwner(node, step.reach)
            .keepOverridden(symbol)
            .enqueueAll(referenced)
            .enqueueInherited(inherited)
            .keepImplementations(step)
            .keepExternalImplementations(node)
            .keepInitialisedFields(node)
      }
    }
  }

  private def collectMemberSymbols(index: Index, symbol: Symbol): Vector[Symbol] =
    index.membersByOwner.getOrElse(symbol, Set.empty).toVector.map(_.symbol).sorted

  private def collectDescendantSymbols(index: Index, symbol: Symbol): Vector[Symbol] =
    collectMemberSymbols(index, symbol).flatMap(member => member +: collectDescendantSymbols(index, member)).distinct

  private def collectSeedSymbols(index: Index, root: DefNode): Vector[Symbol] =
    if (root.kind === DefKind.Extension) root.symbol +: collectMemberSymbols(index, root.symbol)
    else if (root.isContainer) root.symbol +: collectDescendantSymbols(index, root.symbol)
    else Vector(root.symbol)

  @tailrec
  private def drainQueue(walk: Walk): Walk =
    walk.queue.dequeueOption match {
      case Some((step, rest)) =>
        val ahead = walk.copy(queue = rest)
        if (ahead.isProcessed(step)) drainQueue(ahead)
        else drainQueue(ahead.recordProcessed(step).followStep(step))
      case None => walk
    }

  @tailrec
  private def settleImplementations(walk: Walk): Walk = {
    val unimplemented = walk.unimplementedMembers
    if (unimplemented.isEmpty) walk else settleImplementations(drainQueue(walk.enqueueInherited(unimplemented)))
  }

  @tailrec
  private def dropEmptyObjects(index: Index, root: DefNode, kept: Set[Symbol]): Set[Symbol] = {
    val referenced = kept.flatMap(symbol => index.edges.getOrElse(symbol, Set.empty))
    val empty = kept.filter { symbol =>
      index.defs.get(symbol).exists(_.kind === DefKind.Object) &&
      !index.isFlagged(symbol, DefFlag.OpaqueBody) &&
      !index.membersByOwner.getOrElse(symbol, Set.empty).exists(m => kept(m.symbol)) &&
      symbol =!= root.symbol &&
      !referenced(symbol)
    }
    if (empty.isEmpty) kept else dropEmptyObjects(index = index, root = root, kept = kept -- empty)
  }

  def computeSliceResult(index: Index, root: DefNode, options: SliceOptions): SliceResult = {
    val start = Walk(
      index = index,
      options = options,
      kept = Set.empty,
      processed = Map.empty,
      choices = Map.empty,
      queue = Queue.from(collectSeedSymbols(index, root).map(Step(_, Reach.Referenced)))
    )

    val walked = settleImplementations(drainQueue(drainQueue(start).keepReachableConversions))

    SliceResult(
      kept = dropEmptyObjects(index = index, root = root, kept = walked.kept),
      root = root,
      implementationChoices = walked.choices
    )
  }
}
