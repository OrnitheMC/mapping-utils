package net.ornithemc.mappingutils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import net.ornithemc.exceptor.io.ClassEntry;
import net.ornithemc.exceptor.io.ExceptionsFile;
import net.ornithemc.exceptor.io.MethodEntry;

class ExceptionsMerger {

	static ExceptionsFile run(ExceptionsFile client, ExceptionsFile server) {
		return new ExceptionsMerger(client, server).run();
	}

	private final ExceptionsFile client;
	private final ExceptionsFile server;
	private final ExceptionsFile merged;

	private ExceptionsMerger(ExceptionsFile client, ExceptionsFile server) {
		this.client = client;
		this.server = server;
		this.merged = new ExceptionsFile();
	}

	private ExceptionsFile run() {
		for (ClassEntry c : client.classes().values()) {
			ClassEntry s = server.classes().get(c.name());

			if (s == null) {
				// client only mapping - we can add it to merged as is
				merged.classes().put(c.name(), c);
			} else {
				// mapping is present on both sides - merge them
				merged.classes().put(c.name(), mergeClasses(c, s));
			}
		}
		for (ClassEntry s : server.classes().values()) {
			ClassEntry c = client.classes().get(s.name());

			if (c == null) {
				// server only mapping - we can add it to merged as is
				merged.classes().put(s.name(), s);
			} else {
				// mapping is present on both sides - already added to merged
			}
		}

		return merged;
	}

	private ClassEntry mergeClasses(ClassEntry c, ClassEntry s) {
		ClassEntry m = new ClassEntry(c.name());

		for (MethodEntry cm : c.methods().values()) {
			MethodEntry sm = s.methods().get(cm.name() + cm.descriptor());

			if (sm == null) {
				// client only mapping - we can add it to merged as is
				m.methods().put(cm.name() + cm.descriptor(), cm);
			} else {
				// mapping is present on both sides - combine them
				m.methods().put(cm.name() + cm.descriptor(), mergeMethods(cm, sm));
			}
		}
		for (MethodEntry sm : s.methods().values()) {
			MethodEntry cm = c.methods().get(sm.name() + sm.descriptor());

			if (cm == null) {
				// server only mapping - we can add it to merged as is
				m.methods().put(sm.name() + sm.descriptor(), sm);
			} else {
				// mapping is present on both sides - already added to merged
			}
		}

		return m;
	}

	private MethodEntry mergeMethods(MethodEntry c, MethodEntry s) {
		MethodEntry m = new MethodEntry(c.name(), c.descriptor(), new ArrayList<>());

		Set<String> exceptions = new LinkedHashSet<>();
		exceptions.addAll(c.exceptions());
		exceptions.addAll(s.exceptions());
		m.exceptions().addAll(exceptions);

		return m;
	}
}
