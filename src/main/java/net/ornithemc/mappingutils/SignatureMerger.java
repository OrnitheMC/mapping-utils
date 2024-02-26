package net.ornithemc.mappingutils;

import java.util.Objects;

import net.ornithemc.mappingutils.io.sigs.SignatureMappings;
import net.ornithemc.mappingutils.io.sigs.SignatureMappings.ClassMapping;
import net.ornithemc.mappingutils.io.sigs.SignatureMappings.Mapping;
import net.ornithemc.mappingutils.io.sigs.SignatureMappings.MemberMapping;

class SignatureMerger {

	static SignatureMappings run(SignatureMappings client, SignatureMappings server) {
		return new SignatureMerger(client, server).run();
	}

	private final SignatureMappings client;
	private final SignatureMappings server;
	private final SignatureMappings merged;

	private SignatureMerger(SignatureMappings client, SignatureMappings server) {
		this.client = client;
		this.server = server;
		this.merged = new SignatureMappings();
	}

	private SignatureMappings run() {
		for (ClassMapping c : client.getClasses()) {
			ClassMapping s = server.getClass(c.getName());

			if (s == null) {
				// client only mapping - we can add it to merged as is
				addClass(c);
			} else {
				// mapping is present on both sides - check that they match
				if (mappingsMatch(c, s)) {
					mergeClasses(c, s);
				}
			}
		}
		for (ClassMapping s : server.getClasses()) {
			ClassMapping c = client.getClass(s.getName());

			if (c == null) {
				// server only mapping - we can add it to merged as is
				addClass(s);
			} else {
				// mapping is present on both sides - already added to merged
			}
		}

		return merged;
	}

	private void addClass(ClassMapping c) {
		ClassMapping mc = merged.addClass(c.getName(), c.getMode(), c.getSignature());

		for (MemberMapping m : c.getMembers()) {
			mc.addMember(m.getName(), m.getDesc(), m.getMode(), m.getSignature());
		}
	}

	private void mergeClasses(ClassMapping c, ClassMapping s) {
		ClassMapping mc = merged.addClass(c.getName(), c.getMode(), c.getSignature());

		for (MemberMapping cm : c.getMembers()) {
			MemberMapping sm = s.getMember(cm.getName(), cm.getDesc());

			if (sm == null) {
				// client only mapping - we can add it to merged as is
				mc.addMember(cm.getName(), cm.getDesc(), cm.getMode(), cm.getSignature());
			} else {
				// mapping is present on both sides - check that they match
				if (mappingsMatch(cm, sm)) {
					mc.addMember(cm.getName(), cm.getDesc(), cm.getMode(), cm.getSignature());
				}
			}
		}
		for (MemberMapping sm : s.getMembers()) {
			MemberMapping cm = c.getMember(sm.getName(), sm.getDesc());

			if (cm == null) {
				// server only mapping - we can add it to merged as is
				mc.addMember(sm.getName(), sm.getDesc(), sm.getMode(), sm.getSignature());
			} else {
				// nest is present on both sides - already added to merged
			}
		}
	}

	private boolean mappingsMatch(Mapping c, Mapping s) {
		if (c.getMode() != s.getMode()) {
			throw cannotMerge(c, s, "mode does not match");
		}
		if (!Objects.equals(c.getSignature(), s.getSignature())) {
			throw cannotMerge(c, s, "enclosing class name does not match");
		}

		return true;
	}

	private RuntimeException cannotMerge(Mapping  c, Mapping s, String reason) {
		return new IllegalStateException("cannot merge client signature mapping for " + c.toString() + " with server signature mapping for " + s.toString() + ": " + reason);
	}
}
