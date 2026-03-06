import kotlin.test.*

/**
 * A `Hierarchy` stores an arbitrary forest (collection of ordered trees)
 * encoded as DFS order with depths.
 *
 * Each node is represented by its ID and depth.
 * Parent-child relationships are inferred from the depth changes.
 */
interface Hierarchy {

  /** Total number of nodes */
  val size: Int

  /** Returns node ID at the given index */
  fun nodeId(index: Int): Int

  /** Returns depth of the node at the given index */
  fun depth(index: Int): Int

  fun formatString(): String {
    return (0 until size).joinToString(
      separator = ", ",
      prefix = "[",
      postfix = "]"
    ) { i -> "${nodeId(i)}:${depth(i)}" }
  }
}

/**
 * Filters the hierarchy.
 *
 * A node remains in the result only if:
 *  - its node ID satisfies the predicate
 *  - all of its ancestors satisfy the predicate
 */
fun Hierarchy.filter(nodeIdPredicate: (Int) -> Boolean): Hierarchy {

  val resultIds = mutableListOf<Int>()
  val resultDepths = mutableListOf<Int>()

  // Tracks whether the node at a given depth was kept.
  // If a node is removed, its entire subtree must also be removed.
  val ancestorKept = BooleanArray(size + 1)

  for (i in 0 until size) {

    val id = nodeId(i)
    val depth = depth(i)

    // Root nodes have no parent.
    val parentKept = if (depth == 0) true else ancestorKept[depth - 1]

    val keep = parentKept && nodeIdPredicate(id)

    // Store whether this node survives so its children know if they can appear.
    ancestorKept[depth] = keep

    if (keep) {
      resultIds.add(id)
      resultDepths.add(depth)
    }
  }

  return ArrayBasedHierarchy(
    resultIds.toIntArray(),
    resultDepths.toIntArray()
  )
}

class ArrayBasedHierarchy(
  private val myNodeIds: IntArray,
  private val myDepths: IntArray,
) : Hierarchy {

  override val size: Int = myDepths.size

  override fun nodeId(index: Int): Int = myNodeIds[index]

  override fun depth(index: Int): Int = myDepths[index]
}

/**
 * Tests
 */
class FilterTest {

  @Test
  fun testFilterExampleFromPrompt() {

    val unfiltered: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
      intArrayOf(0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2)
    )

    val filteredActual = unfiltered.filter { nodeId -> nodeId % 3 != 0 }

    val filteredExpected: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 5, 8, 10, 11),
      intArrayOf(0, 1, 1, 0, 1, 2)
    )

    assertEquals(
      filteredExpected.formatString(),
      filteredActual.formatString()
    )
  }

  /**
   * Predicate always true -> hierarchy should remain unchanged
   */
  @Test
  fun testAllNodesRemain() {

    val hierarchy: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3),
      intArrayOf(0, 1, 1)
    )

    val filtered = hierarchy.filter { true }

    assertEquals(
      hierarchy.formatString(),
      filtered.formatString()
    )
  }

  /**
   * Removing a root should remove its entire subtree
   */
  @Test
  fun testRootRemovalRemovesSubtree() {

    val hierarchy: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3),
      intArrayOf(0, 1, 2)
    )

    val filtered = hierarchy.filter { it != 1 }

    val expected = ArrayBasedHierarchy(
      intArrayOf(),
      intArrayOf()
    )

    assertEquals(
      expected.formatString(),
      filtered.formatString()
    )
  }

  /**
   * Removing an internal node removes its children as well
   */
  @Test
  fun testInternalNodeRemoval() {

    val hierarchy: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4),
      intArrayOf(0, 1, 2, 1)
    )

    val filtered = hierarchy.filter { it != 2 }

    val expected = ArrayBasedHierarchy(
      intArrayOf(1, 4),
      intArrayOf(0, 1)
    )

    assertEquals(
      expected.formatString(),
      filtered.formatString()
    )
  }

  /**
   * Handles multiple root trees correctly
   */
  @Test
  fun testMultipleRoots() {

    val hierarchy: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4),
      intArrayOf(0, 1, 0, 1)
    )

    val filtered = hierarchy.filter { it % 2 == 0 }

    val expected = ArrayBasedHierarchy(
      intArrayOf(),
      intArrayOf()
    )

    assertEquals(
      expected.formatString(),
      filtered.formatString()
    )
  }

  /**
   * Deep chain of nodes
   */
  @Test
  fun testDeepHierarchy() {

    val hierarchy: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5),
      intArrayOf(0, 1, 2, 3, 4)
    )

    val filtered = hierarchy.filter { it < 4 }

    val expected = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3),
      intArrayOf(0, 1, 2)
    )

    assertEquals(
      expected.formatString(),
      filtered.formatString()
    )
  }
}
