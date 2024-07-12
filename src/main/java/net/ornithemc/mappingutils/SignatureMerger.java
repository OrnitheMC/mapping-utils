package net.ornithemc.mappingutils;

import java.util.Map;
import java.util.Objects;

import io.github.gaming32.signaturechanger.tree.MemberReference;
import io.github.gaming32.signaturechanger.tree.SignatureInfo;
import io.github.gaming32.signaturechanger.tree.SigsClass;
import io.github.gaming32.signaturechanger.tree.SigsFile;

class SignatureMerger {

	static SigsFile run(SigsFile client, SigsFile server) {
		return new SignatureMerger(client, server).run();
	}

	private final SigsFile client;
	private final SigsFile server;
	private final SigsFile merged;

	private SignatureMerger(SigsFile client, SigsFile server) {
		this.client = client;
		this.server = server;
		this.merged = new SigsFile();
	}

	private SigsFile run() {
		for (Map.Entry<String, SigsClass> e : client.classes.entrySet()) {
			String name = e.getKey();
			SigsClass c = e.getValue();
			SigsClass s = server.classes.get(name);

			if (s == null) {
				// client only mapping - we can add it to merged as is
				addClass(name, c);
			} else {
				// mapping is present on both sides - check that they match
				if (signaturesMatch(name, c.signatureInfo, s.signatureInfo)) {
					mergeClasses(name, c, s);
				}
			}
		}
		for (Map.Entry<String, SigsClass> e : server.classes.entrySet()) {
			String name = e.getKey();
			SigsClass s = e.getValue();
			SigsClass c = client.classes.get(name);

			if (c == null) {
				// server only mapping - we can add it to merged as is
				addClass(name, s);
			} else {
				// mapping is present on both sides - already added to merged
			}
		}

		return merged;
	}

	private void addClass(String name, SigsClass c) {
		SigsClass mc = merged.visitClass(name, c.signatureInfo.mode(), c.signatureInfo.signature());

		for (Map.Entry<MemberReference, SignatureInfo> m : c.members.entrySet()) {
			mc.visitMember(m.getKey().name(), m.getKey().desc().getDescriptor(), m.getValue().mode(), m.getValue().signature());
		}
	}

	private void mergeClasses(String name, SigsClass c, SigsClass s) {
		SigsClass mc = merged.visitClass(name, c.signatureInfo.mode(), c.signatureInfo.signature());

		for (Map.Entry<MemberReference, SignatureInfo> e : c.members.entrySet()) {
			MemberReference mr = e.getKey();
			SignatureInfo cm = e.getValue();
			SignatureInfo sm = s.members.get(mr);

			if (sm == null) {
				// client only mapping - we can add it to merged as is
				mc.visitMember(mr.name(), mr.desc().getDescriptor(), cm.mode(), cm.signature());
			} else {
				// mapping is present on both sides - check that they match
				if (signaturesMatch(name + "." + mr.name() + mr.desc().getDescriptor(), cm, sm)) {
					mc.visitMember(mr.name(), mr.desc().getDescriptor(), cm.mode(), cm.signature());
				}
			}
		}
		for (Map.Entry<MemberReference, SignatureInfo> e : s.members.entrySet()) {
			MemberReference mr = e.getKey();
			SignatureInfo sm = e.getValue();
			SignatureInfo cm = c.members.get(mr);

			if (cm == null) {
				// server only mapping - we can add it to merged as is
				mc.visitMember(mr.name(), mr.desc().getDescriptor(), sm.mode(), sm.signature());
			} else {
				// nest is present on both sides - already added to merged
			}
		}
	}

	private boolean signaturesMatch(String ref, SignatureInfo c, SignatureInfo s) {
		if (c.mode() != s.mode()) {
			throw cannotMerge(ref, "mode does not match");
		}
		if (!Objects.equals(c.signature(), s.signature())) {
			throw cannotMerge(ref, "enclosing class name does not match");
		}

		return true;
	}

	private RuntimeException cannotMerge(String ref, String reason) {
		return new IllegalStateException("cannot merge client and server signatures for " + ref + ": " + reason);
	}
}
