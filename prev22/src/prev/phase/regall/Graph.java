package prev.phase.regall;

import prev.data.mem.MemTemp;

import java.util.HashMap;
import java.util.HashSet;

public class Graph {
	HashSet<Vertex> vertices;

	// Maps MemTemps to vertices to enable retrieving and avoid duplication
	HashMap<MemTemp, Vertex> vertexMap;

	public Graph() {
		this.vertices = new HashSet<>();
		this.vertexMap = new HashMap<>();
	}

	public void addVertex(Vertex vertex) {
		// Don't re-add existing vertices
		if (vertexMap.get(vertex.variable) != null) return;

		this.vertices.add(vertex);
		this.vertexMap.put(vertex.variable, vertex);
	}

	public void addVertex(MemTemp temp) {
		// Don't re-add existing vertices
		if (vertexMap.get(temp) != null) return;

		Vertex vertex = new Vertex(temp);
		this.vertices.add(vertex);
		this.vertexMap.put(temp, vertex);
	}

	// Needed to prevent ConcurrentModificationExceptions
	public HashSet<Vertex> vertices() {
		return new HashSet<>(this.vertices);
	}

	public void removeVertex(MemTemp temp) {
		Vertex vertex = this.vertexMap.get(temp);

		if (vertex == null) return;

		this.vertices.remove(vertex);
		this.vertexMap.remove(vertex.variable);

		for (Vertex neighbor : new HashSet<>(vertex.neighbors)) {
			neighbor.removeNeighbor(vertex);
		}

	}

	public void addNeighbors(Vertex vertex1, Vertex vertex2) {
		vertex1.addNeighbor(vertex2);
		vertex2.addNeighbor(vertex1);
	}

	public void addNeighbors(MemTemp temp1, MemTemp temp2) {
		Vertex vertex1 = this.vertexMap.get(temp1);
		Vertex vertex2 = this.vertexMap.get(temp2);

		if (vertex1 == null || vertex2 == null) return;

		vertex1.addNeighbor(vertex2);
		vertex2.addNeighbor(vertex1);
	}

	public HashMap<Vertex, HashSet<Vertex>> getNeighbors() {
		HashMap<Vertex, HashSet<Vertex>> neighbors = new HashMap<>();

		for (Vertex vertex : this.vertices) {
			neighbors.put(vertex, new HashSet<>(vertex.neighbors));
		}

		return neighbors;
	}
}

class Vertex {
	MemTemp variable;
	Integer color = -1;
	Boolean spill = false;
	HashSet<Vertex> neighbors;

	public Vertex(MemTemp variable) {
		this.variable = variable;
		this.neighbors = new HashSet<>();
	}

	public void addNeighbor(Vertex vertex) {
		this.neighbors.add(vertex);
	}

	public void removeNeighbor(Vertex vertex) {
		this.neighbors.remove(vertex);
	}
}
