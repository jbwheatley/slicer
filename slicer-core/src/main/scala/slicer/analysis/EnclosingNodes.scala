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

import slicer.model.{DefNode, Symbol}

private[analysis] final class EnclosingNodes(nodes: Vector[DefNode]) {

  private val opening: Vector[DefNode] = nodes.sortBy(node => (node.start, -node.end))

  def attributeToOwners[A](pairs: Vector[(Int, A)]): Vector[(Symbol, A)] =
    attributeInOffsetOrder(pairs.zipWithIndex.sortBy { case ((offset, _), _) => offset }).sortBy(_._1).map(_._2)

  private def attributeInOffsetOrder[A](asked: Vector[((Int, A), Int)]): Vector[(Int, (Symbol, A))] =
    asked
      .foldLeft((ahead = opening, open = Vector.empty[DefNode], answers = Vector.empty[(Int, (Symbol, A))])) {
        case (reached, ((offset, value), position)) =>
          val (entered, rest) = reached.ahead.span(_.start <= offset)
          val spanning = (reached.open ++ entered).filter(node => offset < node.end)
          (
            ahead = rest,
            open = spanning,
            answers = reached.answers ++ spanning.lastOption.map(node => position -> (node.symbol, value))
          )
      }
      .answers
}
