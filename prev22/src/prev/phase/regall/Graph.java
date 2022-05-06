package prev.phase.regall;

import prev.data.mem.MemTemp;

import java.util.HashMap;
import java.util.HashSet;

public class Graph {
	HashSet<Vertex> vertices = new HashSet<>();

	// Maps MemTemps to vertices to enable retrieving and avoid duplication
	HashMap<MemTemp, Vertex> vertexMap = new HashMap<>();

	void addVertex(MemTemp temp) {
		if (vertexMap.get(temp) != null) return;

		Vertex vertex = new Vertex(temp);
		this.vertices.add(vertex);
		this.vertexMap.put(temp, vertex);
	}

	void removeVertex(Vertex vertex) {
		// Remove vertex from neighbors
		for (Vertex neighbor : this.vertexMap.get(vertex.variable).neighbors) {
			neighbor.neighbors.remove(vertex);
		}

		// Removing the vertex is delegated to the iterator
		// this.vertices.remove(vertex);
		this.vertexMap.remove(vertex.variable);
	}

	void addNeighbors(MemTemp temp1, MemTemp temp2) {
		Vertex vertex1 = this.vertexMap.get(temp1);
		Vertex vertex2 = this.vertexMap.get(temp2);

		vertex1.neighbors.add(vertex2);
		vertex2.neighbors.add(vertex1);
	}

	// Creates a shallow copy of a graph
	Graph copy() {
		Graph newGraph = new Graph();
		newGraph.vertices.addAll(this.vertices);
		newGraph.vertexMap.putAll(this.vertexMap);
		return newGraph;
	}
}

class Vertex {
	MemTemp variable;
	Integer color = -1;
	Boolean potentialSpill = false;
	Boolean spill = false;
	HashSet<Vertex> neighbors = new HashSet<>();

	public Vertex(MemTemp variable) {
		this.variable = variable;
	}
}
