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

package slicer.tui.search

import slicer.model.{DefKind, DefNode}

import cats.syntax.eq.*

private[tui] object FuzzySearch {

  private val contiguousCharacterScore: Int = 16

  private val wordStartScore: Int = 10

  private val longestPenalisedGap: Int = 8

  private val charactersPerLengthPenalty: Int = 4

  private val longestPenalisedName: Int = 10

  private val nearMissScore: Int = 30

  private val editPenalty: Int = 20

  private val sharedPrefixScore: Int = 6

  private val sharedLetterScore: Int = 4

  private val exactNameBonus: Int = 60

  private val wholeWordBonus: Int = 50

  private val wordPrefixBonus: Int = 25

  def rankCandidates(query: String, candidates: Vector[DefNode]): Vector[DefNode] = {
    val (kind, searchTerm) = splitKindKeyword(query)
    val wanted = kind match {
      case Some(wantedKind) => candidates.filter(_.kind === wantedKind)
      case None             => candidates
    }
    if (searchTerm.isEmpty) wanted.sortBy(node => (node.dottedName.length, node.dottedName))
    else
      wanted
        .flatMap(node => scoreNode(searchTerm, node).map(score => (score, node)))
        .sortBy { case (score, node) => (-score, node.dottedName.length, node.dottedName) }
        .map { case (_, node) => node }
  }

  private def splitKindKeyword(query: String): (Option[DefKind], String) = {
    val lowered = query.toLowerCase.dropWhile(_.isWhitespace)
    val keyword = lowered.takeWhile(!_.isWhitespace)
    val keywordKind =
      if (keyword.length < lowered.length) DefKind.searchableByKeyword.get(keyword)
      else None

    keywordKind match {
      case Some(kind) => (Some(kind), lowered.drop(keyword.length).trim)
      case None       => (None, lowered.trim)
    }
  }

  private def scoreNode(searchTerm: String, node: DefNode): Option[Int] = {
    val qualified = if (searchTerm.contains(".")) scoreName(searchTerm, node.dottedName) else None
    (scoreName(searchTerm, node.displayName) ++ qualified).maxOption
  }

  private def scoreName(searchTerm: String, name: String): Option[Int] = {
    val candidate = name.toLowerCase
    val wordStarts = findWordStarts(name)
    val runs = scoreContiguousRun(searchTerm = searchTerm, candidate = candidate, wordStarts = wordStarts)
    val aligned = scoreWordStartMatch(searchTerm = searchTerm, candidate = candidate, wordStarts = wordStarts)
    val typos = scoreNearMiss(searchTerm = searchTerm, candidate = candidate, wordStarts = wordStarts)

    (runs ++ aligned ++ typos).maxOption
      .map(score =>
        score - math.min(candidate.length / charactersPerLengthPenalty, longestPenalisedName) + wordBonus(
          searchTerm = searchTerm,
          name = name,
          wordStarts = wordStarts
        )
      )
  }

  private def scoreContiguousRun(searchTerm: String, candidate: String, wordStarts: Set[Int]): Option[Int] =
    Option(candidate.indexOf(searchTerm))
      .filter(_ >= 0)
      .map(at =>
        contiguousCharacterScore * (searchTerm.length - 1) +
          (if (wordStarts.contains(at)) wordStartScore else 0) -
          math.min(at, longestPenalisedGap)
      )

  private def scoreWordStartMatch(searchTerm: String, candidate: String, wordStarts: Set[Int]): Option[Int] = {
    val starts =
      wordStarts.toVector.filter(at => searchTerm.headOption.exists(first => candidate.lift(at).contains(first)))
    val reached =
      searchTerm
        .drop(1)
        .foldLeft(starts.map(at => at -> (wordStartScore - math.min(at, longestPenalisedGap))).toMap) {
          (walked, wanted) =>
            walked.toVector
              .flatMap { case (last, score) =>
                advanceMatchPositions(
                  wanted = wanted,
                  candidate = candidate,
                  wordStarts = wordStarts,
                  last = last,
                  score = score
                )
              }
              .groupMapReduce { case (at, _) => at } { case (_, score) => score }(math.max)
        }
    reached.values.maxOption
  }

  private def advanceMatchPositions(
      wanted: Char,
      candidate: String,
      wordStarts: Set[Int],
      last: Int,
      score: Int
  ): Vector[(Int, Int)] = {
    val onwards = wordStarts.toVector.filter(_ > last) ++ Vector(last + 1)
    onwards
      .filter(at => candidate.lift(at).contains(wanted))
      .map(at =>
        at -> (score +
          (if (at === last + 1) contiguousCharacterScore else wordStartScore) -
          math.min(at - last - 1, longestPenalisedGap))
      )
  }

  private def scoreNearMiss(searchTerm: String, candidate: String, wordStarts: Set[Int]): Option[Int] = {
    val budget = typoBudget(searchTerm)
    if (budget === 0) None
    else
      (splitIntoWords(candidate, wordStarts) :+ candidate)
        .map(word =>
          (editDistance(searchTerm, word), countSharedPrefix(searchTerm, word), countSharedLetters(searchTerm, word))
        )
        .filter { case (distance, _, _) => distance <= budget }
        .map { case (distance, prefix, letters) =>
          nearMissScore - editPenalty * distance + sharedPrefixScore * prefix + sharedLetterScore * letters
        }
        .maxOption
  }

  private def countSharedPrefix(searchTerm: String, word: String): Int =
    searchTerm.lazyZip(word).takeWhile { case (wanted, found) => wanted === found }.size

  private def countSharedLetters(searchTerm: String, word: String): Int = {
    val available = word.groupMapReduce(identity)(_ => 1)(_ + _)
    searchTerm
      .foldLeft((unmatched = available, shared = 0)) { (counted, wanted) =>
        if (counted.unmatched.getOrElse(wanted, 0) > 0)
          (unmatched = counted.unmatched.updated(wanted, counted.unmatched(wanted) - 1), shared = counted.shared + 1)
        else counted
      }
      .shared
  }

  private def typoBudget(searchTerm: String): Int =
    if (searchTerm.length < 4) 0
    else if (searchTerm.length < 5) 1
    else 2

  private def editDistance(left: String, right: String): Int = {
    val far = Int.MaxValue / 2
    val (_, last) = left.zipWithIndex.foldLeft((Vector.empty[Int], 0.to(right.length).toVector)) {
      case ((twoBack, previous), (leftChar, row)) =>
        val filled = right.zipWithIndex.foldLeft(Vector(row + 1)) { case (cells, (rightChar, column)) =>
          val cost = if (leftChar === rightChar) 0 else 1
          val transposed =
            if (row > 0 && column > 0 && leftChar === right(column - 1) && left(row - 1) === rightChar)
              twoBack(column - 1) + cost
            else far
          cells :+ math.min(
            math.min(previous(column) + cost, cells(column) + 1),
            math.min(previous(column + 1) + 1, transposed)
          )
        }
        (previous, filled)
    }
    last.last
  }

  private def findWordStarts(name: String): Set[Int] =
    name.zipWithIndex.collect {
      case (c, index) if index === 0 || c.isUpper || c.isDigit || isSeparator(c) => index
    }.toSet

  private def wordBonus(searchTerm: String, name: String, wordStarts: Set[Int]): Int = {
    val words = splitIntoWords(name, wordStarts)
    if (name.equalsIgnoreCase(searchTerm)) exactNameBonus
    else if (words.contains(searchTerm)) wholeWordBonus
    else if (words.exists(_.startsWith(searchTerm))) wordPrefixBonus
    else 0
  }

  private def splitIntoWords(name: String, wordStarts: Set[Int]): Vector[String] = {
    val lowered = name.toLowerCase
    val starts = wordStarts.toVector.sorted
    starts
      .zip(starts.drop(1) :+ lowered.length)
      .map { case (start, next) => lowered.slice(start, next).filterNot(isSeparator) }
      .filter(_.nonEmpty)
  }

  private def isSeparator(c: Char): Boolean = c === '_' || c === '$'
}
