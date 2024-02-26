package net.ornithemc.mappingutils;

import java.util.Objects;

import net.ornithemc.nester.nest.Nest;
import net.ornithemc.nester.nest.Nests;

class NestsMerger {

	static Nests run(Nests client, Nests server) {
		return new NestsMerger(client, server).run();
	}

	private final Nests client;
	private final Nests server;
	private final Nests merged;

	private NestsMerger(Nests client, Nests server) {
		this.client = client;
		this.server = server;
		this.merged = Nests.empty();
	}

	private Nests run() {
		for (Nest c : client) {
			Nest s = server.get(c.className);

			if (s == null) {
				// client only nest - we can add it to merged as is
				merged.add(c);
			} else {
				// nest is present on both sides - check that they match
				if (nestsMatch(c, s)) {
					merged.add(c);
				}
			}
		}
		for (Nest s : server) {
			Nest c = client.get(s.className);

			if (c == null) {
				// server only nest - we can add it to merged as is
				merged.add(s);
			} else {
				// nest is present on both sides - already added to merged
			}
		}

		return merged;
	}

	private boolean nestsMatch(Nest c, Nest s) {
		if (c.type != s.type) {
			throw cannotMerge(c, s, "type does not match");
		}
		if (!Objects.equals(c.enclClassName, s.enclClassName)) {
			throw cannotMerge(c, s, "enclosing class name does not match");
		}
		if (!Objects.equals(c.enclMethodName, s.enclMethodName)) {
			throw cannotMerge(c, s, "enclosing method name does not match");
		}
		if (!Objects.equals(c.enclMethodDesc, s.enclMethodDesc)) {
			throw cannotMerge(c, s, "enclosing method descriptor does not match");
		}
		if (!Objects.equals(c.innerName, s.innerName)) {
			throw cannotMerge(c, s, "inner name does not match");
		}
		if (c.access != s.access) {
			throw cannotMerge(c, s, "access flags does not match");
		}

		return true;
	}

	private RuntimeException cannotMerge(Nest  c, Nest s, String reason) {
		return new IllegalStateException("cannot merge client nest " + c.className + " with server nest " + s.className + ": " + reason);
	}
}
