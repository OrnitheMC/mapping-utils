package net.ornithemc.mappingutils.io.matcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

	static final String UNMATCHABLE = "u";
	static final String CLASS = "c";
	static final String FIELD = "f";
	static final String METHOD = "m";
	static final String PARAMETER = "ma";
	static final String VARIABLE = "mv";
	static final String CLASS_UNMATCHABLE = CLASS + UNMATCHABLE;
	static final String FIELD_UNMATCHABLE = FIELD + UNMATCHABLE;
	static final String METHOD_UNMATCHABLE = METHOD + UNMATCHABLE;
	static final String PARAMETER_UNMATCHABLE = PARAMETER + UNMATCHABLE;
	static final String VARIABLE_UNMATCHABLE = VARIABLE + UNMATCHABLE;

	static final int CLASS_INDENTS = 0;
	static final int FIELD_INDENTS = 1;
	static final int METHOD_INDENTS = 1;
	static final int PARAMETER_INDENTS = 2;
	static final int VARIABLE_INDENTS = 2;

	String header = "";

	private final Map<MatchSide, List<InputFile>> input = new EnumMap<>(MatchSide.class);
	private final List<InputFile> sharedClasspath = new ArrayList<>();
	private final Map<MatchSide, List<InputFile>> classpath = new EnumMap<>(MatchSide.class);

	private final Map<MatchSide, String> nonObfClassPattern = new EnumMap<>(MatchSide.class);
	private final Map<MatchSide, String> nonObfMemberPattern = new EnumMap<>(MatchSide.class);

	private final Collection<ClassMatch> classMatches = new LinkedHashSet<>();
	private final Map<MatchSide, Map<String, ClassMatch>> classMatchesBySide = new EnumMap<>(MatchSide.class);

	List<InputFile> getInput(MatchSide side) {
		return input.computeIfAbsent(side, key -> new ArrayList<>());
	}

	List<InputFile> getSharedClasspath() {
		return sharedClasspath;
	}

	List<InputFile> getClasspath(MatchSide side) {
		return classpath.computeIfAbsent(side, key -> new ArrayList<>());
	}

	String getNonObfClassPattern(MatchSide side) {
		return nonObfClassPattern.get(side);
	}

	void setNonObfClassPattern(MatchSide side, String pattern) {
		nonObfClassPattern.put(side, pattern);
	}

	String getNonObfMemberPattern(MatchSide side) {
		return nonObfMemberPattern.get(side);
	}

	void setNonObfMemberPattern(MatchSide side, String pattern) {
		nonObfMemberPattern.put(side, pattern);
	}

	private Map<String, ClassMatch> getMatches(MatchSide side) {
		return classMatchesBySide.computeIfAbsent(side, key -> new LinkedHashMap<>());
	}

	public ClassMatch addClass(String a, String b) {
		return addClass(new ClassMatch(a, b));
	}

	public ClassMatch addClass(ClassMatch c) {
		classMatches.add(c);
		for (MatchSide side : MatchSide.values()) {
			if (c.get(side) != null) {
				getMatches(side).compute(c.get(side), (key, value) -> {
					return checkReplace(value, c);
				});
			}
		}

		return c;
	}

	public ClassMatch getClass(String name, MatchSide side) {
		return getClassByDesc("L" + name + ";", side);
	}

	public ClassMatch getClassByDesc(String desc, MatchSide side) {
		return getMatches(side).get(desc);
	}

	public Collection<ClassMatch> getClasses() {
		return Collections.unmodifiableCollection(classMatches);
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
			return getClass().getSimpleName() + "[" + a + " - " + b + "]";
		}

		public final String get(MatchSide side) {
			return side == MatchSide.A ? a : b;
		}

		public final boolean matched() {
			return a != null && b != null;
		}
	}

	public static class ClassMatch extends Match<ClassMatch> {

		private final Collection<FieldMatch> fieldMatches;
		private final Map<MatchSide, Map<String, FieldMatch>> fieldMatchesBySide;
		private final Collection<MethodMatch> methodMatches;
		private final Map<MatchSide, Map<String, MethodMatch>> methodMatchesBySide;

		private String nameA;
		private String nameB;

		private ClassMatch(String a, String b) {
			super(a, b);

			this.nameA = (this.a == null) ? null : this.a.substring(1, this.a.length() - 1);
			this.nameB = (this.b == null) ? null : this.b.substring(1, this.b.length() - 1);

			this.fieldMatches = new LinkedHashSet<>();
			this.fieldMatchesBySide = new EnumMap<>(MatchSide.class);
			this.methodMatches = new LinkedHashSet<>();
			this.methodMatchesBySide = new EnumMap<>(MatchSide.class);
		}

		public String name(MatchSide side) {
			return side == MatchSide.A ? nameA : nameB;
		}

		private Map<String, FieldMatch> getFieldMatches(MatchSide side) {
			return fieldMatchesBySide.computeIfAbsent(side, key -> new LinkedHashMap<>());
		}

		private Map<String, MethodMatch> getMethodMatches(MatchSide side) {
			return methodMatchesBySide.computeIfAbsent(side, key -> new LinkedHashMap<>());
		}

		public FieldMatch addField(String a, String b) {
			return addField(new FieldMatch(a, b));
		}

		private FieldMatch addField(FieldMatch f) {
			f.parent = this;

			fieldMatches.add(f);
			for (MatchSide side : MatchSide.values()) {
				if (f.get(side) != null) {
					getFieldMatches(side).compute(f.get(side), (key, value) -> {
						return checkReplace(value, f);
					});
				}
			}

			return f;
		}

		public MethodMatch addMethod(String a, String b) {
			return addMethod(new MethodMatch(a, b));
		}

		private MethodMatch addMethod(MethodMatch m) {
			m.parent = this;

			methodMatches.add(m);
			for (MatchSide side : MatchSide.values()) {
				if (m.get(side) != null) {
					getMethodMatches(side).compute(m.get(side), (key, value) -> {
						return checkReplace(value, m);
					});
				}
			}

			return m;
		}

		public FieldMatch getField(String name, String desc, MatchSide side) {
			return getFieldMatches(side).get(name + ";;" + desc);
		}

		public MethodMatch getMethod(String name, String desc, MatchSide side) {
			return getMethodMatches(side).get(name + desc);
		}

		public Collection<FieldMatch> getFields() {
			return Collections.unmodifiableCollection(fieldMatches);
		}

		public Collection<MethodMatch> getMethods() {
			return Collections.unmodifiableCollection(methodMatches);
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

			this.nameA = (this.a == null) ? null : this.a.substring(0, this.a.indexOf(';'));
			this.nameB = (this.b == null) ? null : this.b.substring(0, this.b.indexOf(';'));
			this.descA = (this.a == null) ? null : this.a.substring(this.a.indexOf(';') + 2);
			this.descB = (this.b == null) ? null : this.b.substring(this.b.indexOf(';') + 2);
		}

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

		private final Collection<ParameterMatch> parameterMatches;
		private final Map<MatchSide, Map<String, ParameterMatch>> parameterMatchesBySide;
		private final Collection<VariableMatch> variableMatches;
		private final Map<MatchSide, Map<String, VariableMatch>> variableMatchesBySide;

		private final String nameA;
		private final String nameB;
		private final String descA;
		private final String descB;

		private ClassMatch parent;

		private MethodMatch(String a, String b) {
			super(a, b);

			this.nameA = (this.a == null) ? null : this.a.substring(0, this.a.indexOf('('));
			this.nameB = (this.b == null) ? null : this.b.substring(0, this.b.indexOf('('));
			this.descA = (this.a == null) ? null : this.a.substring(this.a.indexOf('('));
			this.descB = (this.b == null) ? null : this.b.substring(this.b.indexOf('('));

			this.parameterMatches = new LinkedHashSet<>();
			this.parameterMatchesBySide = new EnumMap<>(MatchSide.class);
			this.variableMatches = new LinkedHashSet<>();
			this.variableMatchesBySide = new EnumMap<>(MatchSide.class);
		}

		public String name(MatchSide side) {
			return side == MatchSide.A ? nameA : nameB;
		}

		public String desc(MatchSide side) {
			return side == MatchSide.A ? descA : descB;
		}

		public ClassMatch getParent() {
			return parent;
		}

		private Map<String, ParameterMatch> getParameterMatches(MatchSide side) {
			return parameterMatchesBySide.computeIfAbsent(side, key -> new LinkedHashMap<>());
		}

		private Map<String, VariableMatch> getVariableMatches(MatchSide side) {
			return variableMatchesBySide.computeIfAbsent(side, key -> new LinkedHashMap<>());
		}

		public ParameterMatch addParameter(String a, String b) {
			return addParameter(new ParameterMatch(a, b));
		}

		private ParameterMatch addParameter(ParameterMatch p) {
			p.parent = this;

			parameterMatches.add(p);
			for (MatchSide side : MatchSide.values()) {
				if (p.get(side) != null) {
					getParameterMatches(side).compute(p.get(side), (key, value) -> {
						return checkReplace(value, p);
					});
				}
			}

			return p;
		}

		public VariableMatch addVariable(String a, String b) {
			return addVariable(new VariableMatch(a, b));
		}

		private VariableMatch addVariable(VariableMatch v) {
			v.parent = this;

			variableMatches.add(v);
			for (MatchSide side : MatchSide.values()) {
				if (v.get(side) != null) {
					getVariableMatches(side).compute(v.get(side), (key, value) -> {
						return checkReplace(value, v);
					});
				}
			}

			return v;
		}

		public ParameterMatch getParemeter(int index, MatchSide side) {
			return getParameterMatches(side).get(Integer.toString(index));
		}

		public VariableMatch getVariable(int index, MatchSide side) {
			return getVariableMatches(side).get(Integer.toString(index));
		}

		public Collection<ParameterMatch> getParameters() {
			return Collections.unmodifiableCollection(parameterMatches);
		}

		public Collection<VariableMatch> getVariables() {
			return Collections.unmodifiableCollection(variableMatches);
		}
	}

	public static class ParameterMatch extends Match<ParameterMatch> {

		private final Integer indexA;
		private final Integer indexB;

		private MethodMatch parent;

		private ParameterMatch(String a, String b) {
			super(a, b);

			this.indexA = (this.a == null) ? null : Integer.parseInt(this.a);
			this.indexB = (this.b == null) ? null : Integer.parseInt(this.b);

			if ((this.indexA != null && this.indexA < 0) || (this.indexB != null && this.indexB < 0)) {
				throw new IllegalArgumentException("parameter index cannot be negative!");
			}
		}

		public Integer index(MatchSide side) {
			return side == MatchSide.A ? indexA : indexB;
		}

		public MethodMatch getParent() {
			return parent;
		}
	}

	public static class VariableMatch extends Match<VariableMatch> {

		private final Integer indexA;
		private final Integer indexB;

		private MethodMatch parent;

		private VariableMatch(String a, String b) {
			super(a, b);

			this.indexA = (this.a == null) ? null : Integer.parseInt(this.a);
			this.indexB = (this.b == null) ? null : Integer.parseInt(this.b);

			if ((this.indexA != null && this.indexA < 0) || (this.indexB != null && this.indexB < 0)) {
				throw new IllegalArgumentException("LVT index cannot be negative!");
			}
		}

		public Integer index(MatchSide side) {
			return side == MatchSide.A ? indexA : indexB;
		}

		public MethodMatch getParent() {
			return parent;
		}
	}

	private static <T extends Match<T>> T checkReplace(T o, T n) {
		if (o != null && n != null) {
			System.err.println("Match conflict found! " + o + " and " + n + " target the same source!");
		}

		return n;
	}
}
