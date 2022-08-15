package net.ornithemc.mappingutils.io.matcher;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class Matches {

	static final String INPUT_SRC = "a:";
	static final String INPUT_DST = "b:";
	static final String SHARED_CLASSPATH = "cp:";
	static final String CLASSPATH_SRC = "cp a:";
	static final String CLASSPATH_DST = "cp b:";

	static final String NON_OBF_CLASS_SRC = "non-obf cls a";
	static final String NON_OBF_CLASS_DST = "non-obf cls b";
	static final String NON_OBF_MEMBER_SRC = "non-obf mem a";
	static final String NON_OBF_MEMBER_DST = "non-obf mem b";

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

	public ClassMatch addClass(String src, String dst) {
		return addClass(new ClassMatch(src, dst));
	}

	public ClassMatch addClass(ClassMatch c) {
		return classMatches.compute(c.key(MatchSide.SRC), (key, value) -> {
			return checkReplace(value, c);
		});
	}

	public ClassMatch getClass(String name) {
		return getClassByDesc("L" + name + ";");
	}

	public ClassMatch getClassByDesc(String desc) {
		return classMatches.get(desc);
	}

	public Collection<ClassMatch> getClasses() {
		return classMatches.values();
	}

	protected Matches empty() {
		return new Matches();
	}

	public static abstract class Match<T extends Match<T>> {

		protected String src;
		protected String dst;

		protected Match(String src, String dst) {
			this.src = src;
			this.dst = dst;
		}

		@Override
		public final String toString() {
			return getClass().getSimpleName() + "[" + key(MatchSide.SRC) + " -> " + key(MatchSide.DST) + "]";
		}

		public final String get(MatchSide side) {
			return side == MatchSide.SRC ? src : dst;
		}

		protected final String key(MatchSide side) {
			return side == MatchSide.SRC ? src : dst;
		}

		public abstract String name(MatchSide side);

	}

	public static class ClassMatch extends Match<ClassMatch> {

		private final Map<String, FieldMatch> fieldMatches;
		private final Map<String, MethodMatch> methodMatches;

		private String srcName;
		private String dstName;

		private ClassMatch(String src, String dst) {
			super(src, dst);

			this.srcName = this.src.substring(1, this.src.length() - 1);
			this.dstName = this.dst.substring(1, this.dst.length() - 1);

			this.fieldMatches = new LinkedHashMap<>();
			this.methodMatches = new LinkedHashMap<>();
		}

		@Override
		public String name(MatchSide side) {
			return side == MatchSide.SRC ? srcName : dstName;
		}

		public FieldMatch addField(String src, String dst) {
			return addField(new FieldMatch(src, dst));
		}

		private FieldMatch addField(FieldMatch f) {
			f.parent = this;

			return fieldMatches.compute(f.key(MatchSide.SRC), (key, value) -> {
				return checkReplace(value, f);
			});
		}

		public MethodMatch addMethod(String src, String dst) {
			return addMethod(new MethodMatch(src, dst));
		}

		private MethodMatch addMethod(MethodMatch m) {
			m.parent = this;

			return methodMatches.compute(m.key(MatchSide.SRC), (key, value) -> {
				return checkReplace(value, m);
			});
		}

		public FieldMatch getField(String name, String desc) {
			return fieldMatches.get(name + ";;" + desc);
		}

		public MethodMatch getMethod(String name, String desc) {
			return methodMatches.get(name + desc);
		}

		public Collection<FieldMatch> getFields() {
			return fieldMatches.values();
		}

		public Collection<MethodMatch> getMethods() {
			return methodMatches.values();
		}
	}

	public static class FieldMatch extends Match<FieldMatch> {

		private final String srcName;
		private final String dstName;
		private final String srcDesc;
		private final String dstDesc;

		private ClassMatch parent;

		private FieldMatch(String src, String dst) {
			super(src, dst);

			this.srcName = this.src.substring(0, this.src.indexOf(';'));
			this.dstName = this.dst.substring(0, this.dst.indexOf(';'));
			this.srcDesc = this.src.substring(this.src.indexOf(';') + 2);
			this.dstDesc = this.dst.substring(this.dst.indexOf(';') + 2);
		}

		@Override
		public String name(MatchSide side) {
			return side == MatchSide.SRC ? srcName : dstName;
		}

		public String desc(MatchSide side) {
			return side == MatchSide.SRC ? srcDesc : dstDesc;
		}

		public ClassMatch getParent() {
			return parent;
		}
	}

	public static class MethodMatch extends Match<MethodMatch> {

		private final Map<String, ParameterMatch> parameterMatches;

		private final String srcName;
		private final String dstName;
		private final String srcDesc;
		private final String dstDesc;

		private ClassMatch parent;

		private MethodMatch(String src, String dst) {
			super(src, dst);

			this.parameterMatches = new LinkedHashMap<>();

			this.srcName = this.src.substring(0, this.src.indexOf('('));
			this.dstName = this.dst.substring(0, this.dst.indexOf('('));
			this.srcDesc = this.src.substring(this.src.indexOf('('));
			this.dstDesc = this.dst.substring(this.dst.indexOf('('));
		}

		@Override
		public String name(MatchSide side) {
			return side == MatchSide.SRC ? srcName : dstName;
		}

		public String desc(MatchSide side) {
			return side == MatchSide.SRC ? srcDesc : dstDesc;
		}

		public ClassMatch getParent() {
			return parent;
		}

		public ParameterMatch addParameter(int srcIndex, int dstIndex) {
			return addParameter(new ParameterMatch(srcIndex, dstIndex));
		}

		private ParameterMatch addParameter(ParameterMatch p) {
			p.parent = this;

			return parameterMatches.compute(p.key(MatchSide.SRC), (key, value) -> {
				return checkReplace(value, p);
			});
		}

		public ParameterMatch getParemeter(int index) {
			return parameterMatches.get(Integer.toString(index));
		}

		public Collection<ParameterMatch> getParameters() {
			return parameterMatches.values();
		}
	}

	public static class ParameterMatch extends Match<ParameterMatch> {

		private final int srcIndex;
		private final int dstIndex;

		private MethodMatch parent;

		private ParameterMatch(int srcIndex, int dstIndex) {
			super(Integer.toString(srcIndex), Integer.toString(dstIndex));

			if (srcIndex < 0 || dstIndex < 0) {
				throw new IllegalArgumentException("parameter index cannot be negative!");
			}

			this.srcIndex = srcIndex;
			this.dstIndex = dstIndex;
		}

		@Override
		public String name(MatchSide side) {
			return null;
		}

		public int index(MatchSide side) {
			return side == MatchSide.SRC ? srcIndex : dstIndex;
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
