package net.ornithemc.mappingutils.io.matcher;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class Matches {

	static final String INPUT_A = "a:";
	static final String INPUT_B = "b:";
	static final String SHARED_CLASSPATH = "cp:";
	static final String CLASSPATH_A = "cp a:";
	static final String CLASSPATH_B = "cp b:";

	static final String NON_OBF_CLASS_A = "non-obf cls a";
	static final String NON_OBF_CLASS_B = "non-obf cls b";
	static final String NON_OBF_MEMBER_A = "non-obf mem a";
	static final String NON_OBF_MEMBER_B = "non-obf mem b";

	static final String CLASS = "c";
	static final String FIELD = "f";
	static final String METHOD = "m";
	static final String PARAMETER = "ma";
	static final String VARIABLE = "mv";
	static final String CLASS_UNMATCHABLE = "cu";
	static final String FIELD_UNMATCHABLE = "fu";
	static final String METHOD_UNMATCHABLE = "mu";
	static final String PARAMETER_UNMATCHABLE = "mau";
	static final String VARIABLE_UNMATCHABLE = "mvu";

	static final int CLASS_INDENTS = 0;
	static final int FIELD_INDENTS = 1;
	static final int METHOD_INDENTS = 1;
	static final int PARAMETER_INDENTS = 2;

	private final Map<String, ClassMatch> classMatches = new LinkedHashMap<>();

	public ClassMatch addClass(String a, String b) {
		return addClass(new ClassMatch(a, b));
	}

	public ClassMatch addClass(ClassMatch c) {
		return classMatches.compute(c.key(MatchSide.A), (key, value) -> {
			return checkReplace(value, c);
		});
	}

	public ClassMatch getClass(String nameA) {
		return getClassByDesc("L" + nameA + ";");
	}

	public ClassMatch getClassByDesc(String a) {
		return classMatches.get(a);
	}

	public Collection<ClassMatch> getClasses() {
		return classMatches.values();
	}

	public static abstract class Match<T extends Match<T>> {

		protected String a;
		protected String b;

		protected Match(String a, String b) {
			this.a = a;
			this.b = b;
		}

		@Override
		public final String toString() {
			return getClass().getSimpleName() + "[" + key(MatchSide.A) + " - " + key(MatchSide.B) + "]";
		}

		public final String get(MatchSide side) {
			return side == MatchSide.A ? a : b;
		}

		protected final String key(MatchSide side) {
			return side == MatchSide.A ? a : b;
		}

		public abstract String name(MatchSide side);

	}

	public static class ClassMatch extends Match<ClassMatch> {

		private final Map<String, FieldMatch> fieldMatches;
		private final Map<String, MethodMatch> methodMatches;

		private String nameA;
		private String nameB;

		private ClassMatch(String a, String b) {
			super(a, b);

			this.nameA = this.a.substring(1, this.a.length() - 1);
			this.nameB = this.b.substring(1, this.b.length() - 1);

			this.fieldMatches = new LinkedHashMap<>();
			this.methodMatches = new LinkedHashMap<>();
		}

		@Override
		public String name(MatchSide side) {
			return side == MatchSide.A ? nameA : nameB;
		}

		public FieldMatch addField(String a, String b) {
			return addField(new FieldMatch(a, b));
		}

		private FieldMatch addField(FieldMatch f) {
			f.parent = this;

			return fieldMatches.compute(f.key(MatchSide.A), (key, value) -> {
				return checkReplace(value, f);
			});
		}

		public MethodMatch addMethod(String a, String b) {
			return addMethod(new MethodMatch(a, b));
		}

		private MethodMatch addMethod(MethodMatch m) {
			m.parent = this;

			return methodMatches.compute(m.key(MatchSide.A), (key, value) -> {
				return checkReplace(value, m);
			});
		}

		public FieldMatch getField(String nameA, String descA) {
			return fieldMatches.get(nameA + ";;" + descA);
		}

		public MethodMatch getMethod(String nameA, String descA) {
			return methodMatches.get(nameA + descA);
		}

		public Collection<FieldMatch> getFields() {
			return fieldMatches.values();
		}

		public Collection<MethodMatch> getMethods() {
			return methodMatches.values();
		}
	}

	public static class FieldMatch extends Match<FieldMatch> {

		private final String nameA;
		private final String nameB;
		private final String descA;
		private final String descB;

		private ClassMatch parent;

		private FieldMatch(String a, String b) {
			super(a, b);

			this.nameA = this.a.substring(0, this.a.indexOf(';'));
			this.nameB = this.b.substring(0, this.b.indexOf(';'));
			this.descA = this.a.substring(this.a.indexOf(';') + 2);
			this.descB = this.b.substring(this.b.indexOf(';') + 2);
		}

		@Override
		public String name(MatchSide side) {
			return side == MatchSide.A ? nameA : nameB;
		}

		public String desc(MatchSide side) {
			return side == MatchSide.A ? descA : descB;
		}

		public ClassMatch getParent() {
			return parent;
		}
	}

	public static class MethodMatch extends Match<MethodMatch> {

		private final Map<String, ParameterMatch> parameterMatches;

		private final String nameA;
		private final String nameB;
		private final String descA;
		private final String descB;

		private ClassMatch parent;

		private MethodMatch(String a, String b) {
			super(a, b);

			this.parameterMatches = new LinkedHashMap<>();

			this.nameA = this.a.substring(0, this.a.indexOf('('));
			this.nameB = this.b.substring(0, this.b.indexOf('('));
			this.descA = this.a.substring(this.a.indexOf('('));
			this.descB = this.b.substring(this.b.indexOf('('));
		}

		@Override
		public String name(MatchSide side) {
			return side == MatchSide.A ? nameA : nameB;
		}

		public String desc(MatchSide side) {
			return side == MatchSide.A ? descA : descB;
		}

		public ClassMatch getParent() {
			return parent;
		}

		public ParameterMatch addParameter(int indexA, int indexB) {
			return addParameter(new ParameterMatch(indexA, indexB));
		}

		private ParameterMatch addParameter(ParameterMatch p) {
			p.parent = this;

			return parameterMatches.compute(p.key(MatchSide.A), (key, value) -> {
				return checkReplace(value, p);
			});
		}

		public ParameterMatch getParemeter(int indexA) {
			return parameterMatches.get(Integer.toString(indexA));
		}

		public Collection<ParameterMatch> getParameters() {
			return parameterMatches.values();
		}
	}

	public static class ParameterMatch extends Match<ParameterMatch> {

		private final int indexA;
		private final int indexB;

		private MethodMatch parent;

		private ParameterMatch(int indexA, int indexB) {
			super(Integer.toString(indexA), Integer.toString(indexB));

			if (indexA < 0 || indexB < 0) {
				throw new IllegalArgumentException("parameter index cannot be negative!");
			}

			this.indexA = indexA;
			this.indexB = indexB;
		}

		@Override
		public String name(MatchSide side) {
			return null;
		}

		public int index(MatchSide side) {
			return side == MatchSide.A ? indexA : indexB;
		}

		public MethodMatch getParent() {
			return parent;
		}
	}

	private static <T extends Match<T>> T checkReplace(T o, T n) {
		if (o != null && n != null) {
			throw new IllegalStateException("Match conflict found! " + o + " and " + n + " target the same source!");
		}

		return n;
	}
}
