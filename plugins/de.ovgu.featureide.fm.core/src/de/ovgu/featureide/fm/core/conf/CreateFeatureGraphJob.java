/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2015  FeatureIDE team, University of Magdeburg, Germany
 *
 * This file is part of FeatureIDE.
 * 
 * FeatureIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * FeatureIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with FeatureIDE.  If not, see <http://www.gnu.org/licenses/>.
 *
 * See http://featureide.cs.ovgu.de/ for further information.
 */
package de.ovgu.featureide.fm.core.conf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.prop4j.And;
import org.prop4j.Implies;
import org.prop4j.Literal;
import org.prop4j.Node;
import org.prop4j.Not;
import org.prop4j.Or;

import de.ovgu.featureide.fm.core.Constraint;
import de.ovgu.featureide.fm.core.Feature;
import de.ovgu.featureide.fm.core.FeatureModel;
import de.ovgu.featureide.fm.core.conf.nodes.And2;
import de.ovgu.featureide.fm.core.conf.nodes.Not2;
import de.ovgu.featureide.fm.core.conf.nodes.Variable;
import de.ovgu.featureide.fm.core.conf.nodes.VariableConfiguration;
import de.ovgu.featureide.fm.core.job.AStoppableJob;

public class CreateFeatureGraphJob extends AStoppableJob {

	private final FeatureModel featureModel;
	private final Collection<Feature> fixedFeatures = new HashSet<>();
	private final Collection<Feature> coreFeatures = new HashSet<>();
	private final Collection<Feature> deadFeatures = new HashSet<>();
	private FeatureGraph featureGraph = null;

	public CreateFeatureGraphJob(FeatureModel featureModel) {
		super("Spliting Feature Model");
		this.featureModel = featureModel;
	}

	@Override
	protected boolean work() throws Exception {
		System.out.println("Computing...");
		coreFeatures.addAll(featureModel.getAnalyser().getCoreFeatures());
		deadFeatures.addAll(featureModel.getAnalyser().getDeadFeatures());
		fixedFeatures.addAll(coreFeatures);
		fixedFeatures.addAll(deadFeatures);
		final List<Constraint> constraints = featureModel.getConstraints();
		final Collection<Feature> features = new LinkedList<Feature>(featureModel.getFeatures());
		features.removeAll(fixedFeatures);

		workMonitor.setMaxAbsoluteWork(1 * features.size() + 1);

		featureGraph = new FeatureGraph(features);

		workMonitor.worked();
		
		VariableConfiguration conf = new VariableConfiguration(features.size());

		for (Feature feature : features) {
			final Feature parent = feature.getParent();
			final String featureName = feature.getName();
			final String parentName = parent.getName();
			if (!fixedFeatures.contains(parent)) {
				featureGraph.implies(featureName, parentName);
				if (parent.isAnd()) {
					if (feature.isMandatory()) {
						featureGraph.implies(parentName, featureName);
					}
				} else {
					if (parent.getChildren().size() == 1) {
						featureGraph.implies(parentName, featureName);
					} else {
						featureGraph.setEdge(parent.getName(), featureName, FeatureGraph.EDGE_1q);
						featureGraph.setEdge(featureName, parentName, FeatureGraph.EDGE_0q);
					}
				}
			}
			if (parent.isAlternative()) {
				if (fixedFeatures.contains(parent) && parent.getChildren().size() == 2) {
					for (Feature sibiling : parent.getChildren()) {
						if (!fixedFeatures.contains(sibiling)) {
							featureGraph.setEdge(featureName, sibiling.getName(), FeatureGraph.EDGE_10);
							featureGraph.setEdge(featureName, sibiling.getName(), FeatureGraph.EDGE_01);
						}
					}
				} else {
					for (Feature sibiling : parent.getChildren()) {
						if (!fixedFeatures.contains(sibiling)) {
							featureGraph.setEdge(featureName, sibiling.getName(), FeatureGraph.EDGE_10);
							featureGraph.setEdge(featureName, sibiling.getName(), FeatureGraph.EDGE_0q);
						}
					}
					
					final ArrayList<Variable> list = new ArrayList<>(parent.getChildren().size() - 1);
					for (Feature sibiling : parent.getChildren()) {
						if (sibiling != feature && !fixedFeatures.contains(sibiling)) {
							list.add(conf.getVariable(featureGraph.getFeatureIndex(sibiling.getName())));
						}
					}
					final And2 and = new And2(list.toArray(new Variable[0]));
				}
			} else if (parent.isOr()) {
				boolean optionalFeature = false;
				for (Feature sibiling : parent.getChildren()) {
					if (coreFeatures.contains(sibiling)) {
						optionalFeature = true;
						break;
					}
				}
				if (!optionalFeature) {
					for (Feature sibiling : parent.getChildren()) {
						if (!fixedFeatures.contains(sibiling)) {
							featureGraph.setEdge(featureName, sibiling.getName(), FeatureGraph.EDGE_0q);
						}
					}
					
					final ArrayList<Variable> list = new ArrayList<>(parent.getChildren().size() - 1);
					for (Feature sibiling : parent.getChildren()) {
						if (sibiling != feature && !fixedFeatures.contains(sibiling)) {
							list.add(conf.getVariable(featureGraph.getFeatureIndex(sibiling.getName())));
						}
					}
					final Not2 not = new Not2(new And2(list.toArray(new Variable[0])));
				}
			}
		}
		for (Constraint constraint : constraints) {
			connect(constraint.getNode());
		}

		featureGraph.clearDiagonal();
		
		final ArrayList<String> featureNames = new ArrayList<>();
		for (Feature feature : features) {
			featureNames.add(feature.getName());
		}

		final DFSThreadPool dfsThread = new DFSThreadPool(featureGraph, featureNames, workMonitor);
		dfsThread.run();
		
		featureModel.setFeatureGraph(featureGraph);

		return true;
	}

	private void collectContainedFeatures(Node node, Set<String> featureNames) {
		if (node instanceof Literal) {
			featureNames.add((String) ((Literal) node).var);
		} else {
			for (Node child : node.getChildren()) {
				collectContainedFeatures(child, featureNames);
			}
		}
	}

	private void buildClique(Node... nodes) {
		final Set<String> featureNames = new HashSet<>();
		for (Node node : nodes) {
			collectContainedFeatures(node, featureNames);
		}
		for (Feature coreFeature : fixedFeatures) {
			featureNames.remove(coreFeature.getName());
		}
		for (String featureName1 : featureNames) {
			for (String featureName2 : featureNames) {
				featureGraph.setEdge(featureName1, featureName2, FeatureGraph.EDGE_0q);
				featureGraph.setEdge(featureName1, featureName2, FeatureGraph.EDGE_1q);
			}
		}
	}

	private void imply(Literal implyNode, Literal impliedNode) {
		int negation = 0;
		if (!impliedNode.positive) {
			negation += 1;
		}
		if (!implyNode.positive) {
			negation += 2;
		}
		final String implyFeatureName = (String) implyNode.var;
		final String impliedFeatureName = (String) impliedNode.var;
		if (!fixedFeatures.contains(featureModel.getFeature(implyFeatureName)) && !fixedFeatures.contains(featureModel.getFeature(impliedFeatureName))) {
			featureGraph.implies(implyFeatureName, impliedFeatureName, negation);
		}
	}

	private Collection<Node> simplify(Node node) {
		final Collection<Node> nodeList = new LinkedList<>();

		node = deMorgan(node);
		node = orToImply(node);
		node = elimnateNot(node);
		if (node instanceof And) {
			final Node[] children = node.getChildren();
			for (Node child : children) {
				nodeList.add(child);
			}
		} else {
			nodeList.add(node);
		}

		return nodeList;
	}

	private Node elimnateNot(Node node) {
		if (node instanceof Not) {
			Node child = node.getChildren()[0];
			if (child instanceof Literal) {
				Literal l = (Literal) child;
				return new Literal(l.var, !l.positive);
			} else if (child instanceof Not) {
				return child.getChildren()[0].clone();
			}
		}
		final Node[] children = node.getChildren();
		if (children != null) {
			for (int i = 0; i < children.length; i++) {
				children[i] = elimnateNot(children[i]);
			}
		}
		return node;
	}

	private Node deMorgan(Node node) {
		if (node instanceof Not) {
			Node child = node.getChildren()[0];
			if (child instanceof And) {
				final Node[] children = child.getChildren();
				final Node[] newChildren = new Node[children.length];
				for (int i = 0; i < children.length; i++) {
					newChildren[i] = new Not(children[i].clone());
				}
				node = new Or(newChildren);
			}
		}
		final Node[] children = node.getChildren();
		if (children != null) {
			for (int i = 0; i < children.length; i++) {
				children[i] = deMorgan(children[i]);
			}
		}
		return node;
	}

	private Node orToImply(Node node) {
		if (node instanceof Or && node.getChildren().length == 2) {
			final Node[] children = node.getChildren();
			return new Implies(new Not(children[0].clone()), children[1].clone());
		} else if (node instanceof And) {
			final Node[] children = node.getChildren();
			for (int i = 0; i < children.length; i++) {
				children[i] = orToImply(children[i]);
			}
		}
		return node;
	}

	private void connect(Node constraintNode2) {
		//TODO simplify nodes: convert to implies, remove not node
		final Collection<Node> nodeList = simplify(constraintNode2);
		for (Node constraintNode : nodeList) {
			if (constraintNode instanceof Implies) {
				final Node leftNode = constraintNode.getChildren()[0];
				final Node rightNode = constraintNode.getChildren()[1];
				if (leftNode instanceof Literal) {
					final Literal implyNode = (Literal) leftNode;
					if (rightNode instanceof Literal) {
						imply(implyNode, (Literal) rightNode);
					} else if (rightNode instanceof And) {
						for (Node impliedNode : rightNode.getChildren()) {
							if (impliedNode instanceof Literal) {
								imply(implyNode, (Literal) impliedNode);
							} else {
								buildClique(implyNode, impliedNode);
							}
						}
					}
				} else if (leftNode instanceof Or) {
					if (rightNode instanceof Literal) {
						for (Node implyNode : leftNode.getChildren()) {
							if (implyNode instanceof Literal) {
								imply((Literal) implyNode, (Literal) rightNode);
							} else {
								buildClique(implyNode, rightNode);
							}
						}
					} else if (rightNode instanceof And) {
						for (Node implyNode : leftNode.getChildren()) {
							if (implyNode instanceof Literal) {
								for (Node impliedNode : rightNode.getChildren()) {
									if (impliedNode instanceof Literal) {
										imply((Literal) implyNode, (Literal) impliedNode);
									} else {
										buildClique(implyNode, impliedNode);
									}
								}
							} else {
								for (Node impliedNode : rightNode.getChildren()) {
									buildClique(implyNode, impliedNode);
								}
							}
						}
					}
				}
			} else {
				//TODO Implement other special cases
				buildClique(constraintNode);
			}
		}
	}

}
