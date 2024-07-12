package net.ornithemc.mappingutils;

import io.github.gaming32.signaturechanger.tree.SigsFile;

import net.ornithemc.nester.nest.Nests;

class SignatureNester {

	static SigsFile run(SigsFile sigs, Nests nests, boolean apply) {
		return new SignatureNester(sigs, nests, apply).run();
	}

	private final SigsFile sigs;
	private final Nests nests;
	private final boolean apply;

	private SignatureNester(SigsFile sigs, Nests nests, boolean apply) {
		this.sigs = sigs;
		this.nests = nests;
		this.apply = apply;
	}

	public SigsFile run() {
		return SignatureMapper.run(sigs, Nester.buildRemapper(nests, apply));
	}
}
