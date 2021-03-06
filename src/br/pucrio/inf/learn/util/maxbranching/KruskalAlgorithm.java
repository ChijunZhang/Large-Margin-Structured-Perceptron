package br.pucrio.inf.learn.util.maxbranching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;

/**
 * Implement a maximum branching algorithm for undirected graphs. A directed
 * graph is given as input and the algorithm selects only one directed edge to
 * represent the undirected edge. For two nodes i and j such that i < j, it
 * tries first the edge (i,j). If this edge doest not exist, it uses the (j,i)
 * edge
 * 
 * @author eraldo
 * 
 */
public class KruskalAlgorithm {

	/**
	 * List of all edges.
	 */
	private ArrayList<SimpleWeightedEdge> edges;

	/**
	 * Whether to avoid negative-weight edges.
	 */
	private boolean onlyPositiveEdges;

	/**
	 * Minimum number of components to stop the algorithm.
	 */
	private int minComponents;

	/**
	 * Simple edge comparator.
	 */
	public final static Comparator<SimpleWeightedEdge> comp = new Comparator<SimpleWeightedEdge>() {
		@Override
		public int compare(SimpleWeightedEdge o1, SimpleWeightedEdge o2) {
			if (o1.weight > o2.weight)
				return -1;
			if (o1.weight < o2.weight)
				return 1;
			return 0;
		}
	};

	/**
	 * Create a undirected maximum branching algorithm (Kruskal) that is able to
	 * handle graphs with up to the given <code>maxNumberOfNodes</code> nodes.
	 * 
	 * @param maxNumberOfNodes
	 */
	public KruskalAlgorithm(int maxNumberOfNodes, int minComponents) {
		this.minComponents = minComponents;
		realloc(maxNumberOfNodes);
	}

	/**
	 * Find the maximum spanning tree for the given graph. The number of nodes
	 * is also given, so that the graph array can be larger than that. The
	 * selected edges are added to the given collection.
	 * 
	 * The algorithm can stop before achieving one unique tree, if in the
	 * constructor, a value greater than 1 is given for the parameter
	 * <code>minComponenets</code>.
	 * 
	 * @param numberOfNodes
	 * @param graph
	 * @param mst
	 * @return the weight of the built tree.
	 */
	public double findMaxBranching(int numberOfNodes, double[][] graph,
			Collection<SimpleWeightedEdge> mst, DisjointSets partition) {
		// Clear the list of all edges.
		edges.clear();
		// Clear MST.
		mst.clear();
		// Add feasible edges with their weights to the list.
		for (int from = 0; from < numberOfNodes; ++from) {
			for (int to = 0; to < numberOfNodes; ++to) {
				if (from == to)
					// Skip auto-cycle arcs.
					continue;
				if (Double.isNaN(graph[from][to]))
					// Skip inexistent edges.
					continue;
				if (onlyPositiveEdges && graph[from][to] < 0d)
					// Skip negative edges, when required so.
					continue;
				// Feasible edge.
				edges.add(new SimpleWeightedEdge(from, to, graph[from][to]));
			}
		}

		// Sort edges in the inverse order of their weights.
		Collections.sort(edges, comp);

		// Initialize the disjoint sets (one component for each node).
		partition.clear(numberOfNodes);

		// Greedily select edges while avoiding cycles.
		double totalWeight = 0d;
		int numComponents = numberOfNodes;
		for (SimpleWeightedEdge edge : edges) {
			/*
			 * If edge connects two components, merge them and add this edge to
			 * the MST.
			 */
			if (partition.unionElements(edge.from, edge.to)) {
				// Add edge to the tree.
				mst.add(edge);
				// Account for its weight.
				totalWeight += edge.weight;
				// Decrement number of components.
				--numComponents;
				// Stop, if achieved the required number of components.
				if (numComponents <= minComponents)
					break;
			}
		}

		// Return the weight of the created tree.
		return totalWeight;
	}

	/**
	 * Guarantee that the internal structures fit the given number of nodes.
	 * 
	 * @param maxNumberOfNodes
	 */
	public void realloc(int maxNumberOfNodes) {
		if (edges != null)
			edges.ensureCapacity(maxNumberOfNodes);
		else
			edges = new ArrayList<SimpleWeightedEdge>(maxNumberOfNodes
					* (maxNumberOfNodes - 1));
	}

	public void setOnlyPositiveEdges(boolean val) {
		onlyPositiveEdges = val;
	}

	public boolean isOnlyPositiveEdges() {
		return onlyPositiveEdges;
	}

	/**
	 * Test the undirected maximum branching algorithm.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		KruskalAlgorithm alg = new KruskalAlgorithm(5, 1);
		double[][] graph = {
				//
				{ 0, 10, 0, 0, 10 }, //
				{ 0, 0, 1, 5, 10 },//
				{ 0, 0, 0, 2, 0 }, //
				{ 0, 0, 0, 0, 5 }, //
				{ 0, 0, 0, 0, 0 }, //
		};
		LinkedList<SimpleWeightedEdge> mst = new LinkedList<SimpleWeightedEdge>();
		DisjointSets partition = new DisjointSets(5);

		alg.findMaxBranching(5, graph, mst, partition);

		for (SimpleWeightedEdge edge : mst)
			System.out.print(String.format("(%d,%d) ", edge.from, edge.to));
	}
}
