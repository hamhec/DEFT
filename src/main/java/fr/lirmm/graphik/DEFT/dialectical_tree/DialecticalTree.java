package fr.lirmm.graphik.DEFT.dialectical_tree;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class DialecticalTree {
	public static final int NOT_LABELED = 0;
	public static final int DEFEATED = 1;
	public static final int UNDEFEATED = 2;
	
	private Argument root;
	private boolean isDefeated = true;
	
	public List<Node> defeaters;
	 
    public DialecticalTree(Argument arg, List<Defeater> defeaters) {
        root = arg;
        this.defeaters = new LinkedList<Node>();
        for(Defeater defeater : defeaters) {
        	this.defeaters.add(new Node(defeater));
        }
    }
    
    public Argument getArgument() {
    	return root;
    }
    
    public boolean isDefeated() {
    	return this.isDefeated;
    }
    
    public static class Node {
        private Defeater data;
        private Node parent;
        private List<Node> children;
        private int label = NOT_LABELED;
        
        public Node(Defeater data) {
        	this.data = data;
        	this.parent = null;
        	this.children = null;
        }
        
        public Node(Defeater data, Node parent, List<Node> children) {
        	this.data = data;
        	this.parent = parent;
        	this.children = children;
        }
        
        public Defeater getData() {
        	return this.data;
        }
        
        public void setParent(Node parent) {
        	this.parent = parent;
        }
        
        public Node getParent() {
        	return this.parent;
        }
        
        public List<Node> getChildren() {
        	return this.children;
        }
        
        public void addDefeater(Node n) {
        	if(null == this.children) this.children = new LinkedList<Node>();
        	this.children.add(n);
        	n.parent = this;
        }
        
        public void setLabel(int status) {
        	this.label = status;
        }
        
        public int getLabel() {
        	return this.label;
        }
        
        /**
    	 * Displays the sub dialectical tree strating from this Node as a string.
    	 */
        public String toString(String str) {
        	StringBuilder s = new StringBuilder();
        	s.append(str);
        	s.append(" <- ");
        	s.append(this.data.argument.toString());
        	if(null != this.children) {
        		for(Node child : this.children) {
        			s.append(child.toString(s.toString()));
        			s.append("\n");
        		}
        	}
        	
        	return s.toString();
        }
    }
    
    /**
	 * Displays the dialectical tree as a string.
	 */
	public String toString() {
		StringBuilder s = new StringBuilder();
		
		for(Node defeater : this.defeaters) {
			s.append(defeater.toString(this.root.toString()));
		}
		
		return s.toString();
	}
}
