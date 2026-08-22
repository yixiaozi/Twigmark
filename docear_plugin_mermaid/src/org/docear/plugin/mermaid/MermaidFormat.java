package org.docear.plugin.mermaid;

import org.freeplane.features.format.IdentityPatternFormat;
import org.freeplane.features.map.NodeModel;

public class MermaidFormat extends IdentityPatternFormat {

	static final String MERMAID_FORMAT = "mermaidPatternFormat";

	MermaidFormat() {
		super(MERMAID_FORMAT);
	}

	@Override
	public boolean canFormat(Class<?> cls) {
		return NodeModel.class.isAssignableFrom(cls);
	}
}
