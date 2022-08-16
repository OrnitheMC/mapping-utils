package net.ornithemc.mappingutils.io;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.objectweb.asm.Type;

import net.ornithemc.mappingutils.MappingUtils;

public class Mappings {

	private final Map<String, ClassMapping> classMappings = new LinkedHashMap<>();

	private Mappings inverted;

	public Mappings() {

	}

	private Mappings(Mappings inverted) {
		this.inverted = inverted;
		inverted.inverted = this;
	}

	public ClassMapping addClass(String src, String dst) {
		return addClass(new ClassMapping(this, src, dst, null));
	}

	private ClassMapping addClass(ClassMapping c) {
		int i = c.src.lastIndexOf('$');

		if (i < 0) {
			return classMappings.compute(c.key(), (key, value) -> {
				return checkReplace(value, c);
			});
		}

		String parentName = c.src.substring(0, i);
		ClassMapping parent = getClass(parentName);

		if (parent == null) {
			throw new IllegalStateException("cannot find parent class mapping " + parentName + " of class mapping " + c);
		}

		return parent.addClass(c);
	}

	public ClassMapping getTopLevelClass(String name) {
		return classMappings.get(name);
	}

	public ClassMapping getClass(String name) {
		int i = name.lastIndexOf('$');
		
		if (i < 0) {
			return getTopLevelClass(name);
		}
		
		String parentName = name.substring(0, i);
		String simple = name.substring(i + 1);
		ClassMapping parent = getClass(parentName);

		if (parent == null) {
			return null;
		}

		return parent.getClass(simple);
	}

	public Collection<ClassMapping> getTopLevelClasses() {
		return classMappings.values();
	}

	public Mappings invert() {
		if (inverted == null) {
			inverted = new Mappings(this);

			for (ClassMapping c : classMappings.values()) {
				inverted.addClass(c.inverted());
			}
		}

		return inverted;
	}

	public Mappings copy() {
		Mappings copy = new Mappings();

		for (ClassMapping c : classMappings.values()) {
			copy.addClass(c.copy());
		}

		return copy;
	}

	public static abstract class Mapping<T extends Mapping<T>> {

		protected Mappings root;
		protected T inverted;

		protected String src;
		protected String dst;
		protected String jav;

		@SuppressWarnings("unchecked")
		private Mapping(Mappings root, T inverted, String src, String dst) {
			this.root = root;

			if (inverted != null) {
				this.inverted = inverted;
				inverted.inverted = (T)this;
			}

			this.src = src;
			this.dst = dst;

			Objects.requireNonNull(this.root);
		}

		@Override
		public final String toString() {
			return getClass().getSimpleName() + "[" + key() + " -> " + dst + "]";
		}

		public final String src() {
			return src;
		}

		public final String get() {
			return dst;
		}

		public final void set(String dst) {
			this.dst = dst;
		}

		public final String getJavadocs() {
			return jav;
		}

		public final void setJavadocs(String jav) {
			this.jav = jav;
		}

		public final T invert() {
			root.invert();

			if (inverted == null) {
				inverted();
			}

			return inverted;
		}

		protected String key() {
			return src;
		}

		protected abstract T inverted();

		protected abstract T copy();

	}

	public static class ClassMapping extends Mapping<ClassMapping> {

		private final Map<String, ClassMapping> classMappings;
		private final Map<String, FieldMapping> fieldMappings;
		private final Map<String, MethodMapping> methodMappings;

		private ClassMapping parent;
		private String simple;

		private ClassMapping(Mappings root, String src, String dst, String simple) {
			this(root, null, src, dst, simple);
		}

		private ClassMapping(Mappings root, ClassMapping inverted, String src, String dst) {
			this(root, inverted, src, dst, null);
		}

		private ClassMapping(Mappings root, ClassMapping inverted, String src, String dst, String simple) {
			super(root, inverted, src, dst);

			this.classMappings = new LinkedHashMap<>();
			this.fieldMappings = new LinkedHashMap<>();
			this.methodMappings = new LinkedHashMap<>();

			this.simple = simple;

			if (this.simple == null) {
				int i = this.src.lastIndexOf('$');

				if (i > 0) {
					this.simple = this.src.substring(i + 1);
				}
			}
		}

		@Override
		protected ClassMapping inverted() {
			inverted = new ClassMapping(root.inverted, this, dst, src);
			inverted.setJavadocs(jav);

			for (ClassMapping c : classMappings.values()) {
				inverted.addClass(c.inverted());
			}
			for (FieldMapping f : fieldMappings.values()) {
				inverted.addField(f.inverted());
			}
			for (MethodMapping m : methodMappings.values()) {
				inverted.addMethod(m.inverted());
			}

			return inverted;
		}

		@Override
		protected ClassMapping copy() {
			ClassMapping copy = new ClassMapping(root, src, dst, simple);
			copy.setJavadocs(jav);

			for (ClassMapping c : classMappings.values()) {
				copy.addClass(c.copy());
			}
			for (FieldMapping f : fieldMappings.values()) {
				copy.addField(f.copy());
			}
			for (MethodMapping m : methodMappings.values()) {
				copy.addMethod(m.copy());
			}

			return copy;
		}

		public String getSimple() {
			return simple;
		}

		public void setSimple(String simple) {
			this.simple = simple;
		}

		public ClassMapping getParentClass() {
			return parent;
		}

		public ClassMapping addClass(String src, String dst, String simple) {
			return addClass(new ClassMapping(root, src, dst, simple));
		}
		
		private ClassMapping addClass(ClassMapping c) {
			c.parent = this;
			
			return classMappings.compute(c.simple, (key, value) -> {
				return checkReplace(value, c);
			});
		}

		public FieldMapping addField(String src, String dst, String desc) {
			return addField(new FieldMapping(root, src, dst, desc));
		}

		private FieldMapping addField(FieldMapping f) {
			f.parent = this;

			return fieldMappings.compute(f.key(), (key, value) -> {
				return checkReplace(value, f);
			});
		}

		public MethodMapping addMethod(String src, String dst, String desc) {
			return addMethod(new MethodMapping(root, src, dst, desc));
		}

		private MethodMapping addMethod(MethodMapping m) {
			m.parent = this;

			return methodMappings.compute(m.key(), (key, value) -> {
				return checkReplace(value, m);
			});
		}

		public ClassMapping getClass(String simple) {
			return classMappings.get(simple);
		}

		public FieldMapping getField(String name, String desc) {
			return fieldMappings.get(name + desc);
		}

		public MethodMapping getMethod(String name, String desc) {
			MethodMapping m = methodMappings.get(name + desc);

			if (m == null && (name.equals("<init>") || name.equals("<clinit>"))) {
				m = addMethod(name, name, desc);
			}

			return m;
		}

		public Collection<ClassMapping> getClasses() {
			return classMappings.values();
		}

		public Collection<FieldMapping> getFields() {
			return fieldMappings.values();
		}

		public Collection<MethodMapping> getMethods() {
			return methodMappings.values();
		}
	}

	public static class FieldMapping extends Mapping<FieldMapping> {

		private ClassMapping parent;
		private String desc;

		private FieldMapping(Mappings root, String src, String dst, String desc) {
			this(root, null, src, dst, desc);
		}

		private FieldMapping(Mappings root, FieldMapping inverted, String src, String dst, String desc) {
			super(root, inverted, src, dst);

			this.desc = desc;
		}

		@Override
		protected String key() {
			return super.key() + desc;
		}

		@Override
		protected FieldMapping inverted() {
			inverted = new FieldMapping(root.inverted, this, dst, src, MappingUtils.updateFieldDescriptor(desc, root));
			inverted.setJavadocs(jav);

			return inverted;
		}

		@Override
		protected FieldMapping copy() {
			FieldMapping copy = new FieldMapping(root, src, dst, desc);
			copy.setJavadocs(jav);

			return copy;
		}

		public String getDesc() {
			return desc;
		}

		public ClassMapping getParent() {
			return parent;
		}
	}

	public static class MethodMapping extends Mapping<MethodMapping> {

		private final Map<String, ParameterMapping> parameterMappings;

		private ClassMapping parent;
		private String desc;
		private int parameterCount;

		private MethodMapping(Mappings root, String src, String dst, String desc) {
			this(root, null, src, dst, desc);
		}

		private MethodMapping(Mappings root, MethodMapping inverted, String src, String dst, String desc) {
			super(root, inverted, src, dst);

			this.parameterMappings = new LinkedHashMap<>();

			this.desc = desc;
			this.parameterCount = -1;
		}

		@Override
		protected String key() {
			return super.key() + desc;
		}

		@Override
		protected MethodMapping inverted() {
			inverted = new MethodMapping(root.inverted, this, dst, src, MappingUtils.updateMethodDescriptor(desc, root));
			inverted.setJavadocs(jav);

			for (ParameterMapping p : parameterMappings.values()) {
				inverted.addParameter(p.inverted());
			}

			return inverted;
		}

		@Override
		protected MethodMapping copy() {
			MethodMapping copy = new MethodMapping(root, src, dst, desc);
			copy.setJavadocs(jav);

			for (ParameterMapping p : parameterMappings.values()) {
				copy.addParameter(p.copy());
			}

			return copy;
		}

		public String getDesc() {
			return desc;
		}

		public int getParameterCount() {
			if (parameterCount < 0) {
				Type type = Type.getMethodType(desc);
				Type[] argTypes = type.getArgumentTypes();

				parameterCount = argTypes.length;
			}

			return parameterCount;
		}

		public ClassMapping getParent() {
			return parent;
		}

		public ParameterMapping addParameter(String src, String dst, int index) {
			return addParameter(new ParameterMapping(root, src, dst, index));
		}

		private ParameterMapping addParameter(ParameterMapping p) {
			if (p.index > getParameterCount()) {
				return null;
			}

			p.parent = this;

			return parameterMappings.compute(p.key(), (key, value) -> {
				return checkReplace(value, p);
			});
		}

		public ParameterMapping getParemeter(int index) {
			return parameterMappings.get(Integer.toString(index));
		}

		public Collection<ParameterMapping> getParameters() {
			return parameterMappings.values();
		}
	}

	public static class ParameterMapping extends Mapping<ParameterMapping> {

		private final int index;

		private MethodMapping parent;

		private ParameterMapping(Mappings root, String src, String dst, int index) {
			this(root, null, src, dst, index);
		}

		private ParameterMapping(Mappings root, ParameterMapping inverted, String src, String dst, int index) {
			super(root, inverted, src, dst);

			if (index < 0) {
				throw new IllegalArgumentException("parameter index cannot be negative!");
			}

			this.index = index;
		}

		@Override
		protected String key() {
			return Integer.toString(index);
		}

		@Override
		protected ParameterMapping inverted() {
			inverted = new ParameterMapping(root.inverted, this, dst, src, index);
			inverted.setJavadocs(jav);

			return inverted;
		}

		@Override
		protected ParameterMapping copy() {
			ParameterMapping copy = new ParameterMapping(root, src, dst, index);
			copy.setJavadocs(jav);

			return copy;
		}

		public int getIndex() {
			return index;
		}

		public MethodMapping getParent() {
			return parent;
		}
	}

	private static <T extends Mapping<T>> T checkReplace(T o, T n) {
		if (o != null && n != null) {
			System.err.println("replacing mapping " + o + " with " + n);
		}

		return n;
	}
}
