/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.runtime.slotted.pipes

import org.neo4j.cypher.internal.compatibility.v3_5.runtime.SlotConfiguration
import org.neo4j.cypher.internal.runtime.interpreted.ExecutionContext
import org.neo4j.cypher.internal.runtime.interpreted.pipes.{Pipe, QueryState}
import org.neo4j.cypher.internal.runtime.slotted.SlottedExecutionContext
import org.neo4j.cypher.internal.runtime.{IndexedPrimitiveNodeWithProperties, NodeValueHit, ResultCreator}

/**
  * Provides helper methods for slotted index pipes that get nodes together with actual property values.
  */
trait IndexSlottedPipeWithValues extends Pipe {

  // Offset of the long slot of node variable
  val offset: Int
  // the indices of the index properties where we will get values
  val propertyIndicesWithValues: Array[Int]
  // the offsets of the ref slots of properties where we will set values
  val propertyOffsets: Array[Int]
  // Number of longs and refs
  val argumentSize: SlotConfiguration.Size

  /**
    * Create an Iterator of ExecutionContexts given an Iterator of tuples of nodes ids and property values,
    * by copying the node and all values into the given context.
    */
  def createResultsFromPrimitiveTupleIterator(state: QueryState, slots: SlotConfiguration, tupleIterator: Iterator[IndexedPrimitiveNodeWithProperties]): Iterator[ExecutionContext] = {
    tupleIterator.map {
      case IndexedPrimitiveNodeWithProperties(node, values) =>
        val slottedContext: SlottedExecutionContext = SlottedExecutionContext(slots)
        state.copyArgumentStateTo(slottedContext, argumentSize.nLongs, argumentSize.nReferences)
        slottedContext.setLongAt(offset, node)
        var i = 0
        while (i < values.length) {
          slottedContext.setRefAt(propertyOffsets(i), values(i))
          i += 1
        }
        slottedContext
    }
  }

  case class CtxResultCreator(state: QueryState, slots: SlotConfiguration) extends ResultCreator[ExecutionContext] {
    override def createResult(nodeValueHit: NodeValueHit): ExecutionContext = {
      val slottedContext: SlottedExecutionContext = SlottedExecutionContext(slots)
      state.copyArgumentStateTo(slottedContext, argumentSize.nLongs, argumentSize.nReferences)
      slottedContext.setLongAt(offset, nodeValueHit.nodeId)
      var i = 0
      while (i < propertyIndicesWithValues.length) {
        val value = nodeValueHit.propertyValue(propertyIndicesWithValues(i))
        slottedContext.setRefAt(propertyOffsets(i), value)
        i += 1
      }
      slottedContext
    }
  }
}
