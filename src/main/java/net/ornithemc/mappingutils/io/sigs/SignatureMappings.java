package net.ornithemc.mappingutils.io.sigs;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class SignatureMappings {

	private final Map<String, ClassMapping> classes = new LinkedHashMap<>();

	public ClassMapping addClass(String name, SignatureMode mode, String signature) {
		return classes.computeIfAbsent(name, (key) -> {
			return new ClassMapping(name, mode, signature);
		});
	}

	public ClassMapping getClass(String name) {
		return classes.get(name);
	}

	public Collection<ClassMapping> getClasses() {
		return classes.values();
	}

	public static class Mapping {

		protected SignatureMode mode;
		protected String signature;

		protected Mapping(SignatureMode mode, String signature) {
			this.mode = mode;
			this.signature = signature;
		}

		public SignatureMode getMode() {
			return mode;
		}

		public void setMode(SignatureMode mode) {
			this.mode = mode;
		}

		public String getSignature() {
			return signature;
		}

		public void setSignature(String signature) {
			this.signature = signature;
		}
	}

	public static class ClassMapping extends Mapping {

		private final Map<String, MemberMapping> members = new LinkedHashMap<>();;

		private String name;

		public ClassMapping(String name, SignatureMode mode, String signature) {
			super(mode, signature);

			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}

		public String getName() {
			return name;
		}

		public MemberMapping addMember(String name, String desc, SignatureMode mode, String signature) {
			return members.computeIfAbsent(name + desc, (key) -> {
				return new MemberMapping(name, desc, mode, signature);
			});
		}

		public MemberMapping getMember(String name, String desc) {
			return members.get(name + desc);
		}

		public Collection<MemberMapping> getMembers() {
			return members.values();
		}
	}

	public static class MemberMapping extends Mapping {

		private String name;
		private String desc;

		public MemberMapping(String name, String desc, SignatureMode mode, String signature) {
			super(mode, signature);

			this.name = name;
			this.desc = desc;
		}

		@Override
		public String toString() {
			return name + desc;
		}

		public String getName() {
			return name;
		}

		public String getDesc() {
			return desc;
		}
	}
}
