package net.ornithemc.mappingutils.io;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.objectweb.asm.Type;

import net.ornithemc.mappingutils.MappingUtils;

public class Mappings {

	private final Map<String, ClassMapping> classMappings = new LinkedHashMap<>();

	private MappingNamespace srcNamespace = MappingNamespace.NONE;
	private MappingNamespace dstNamespace = MappingNamespace.NONE;

	private Mappings inverted;

	public Mappings() {

	}

	public Mappings(MappingNamespace srcNamespace, MappingNamespace dstNamespace) {
		this();

		this.srcNamespace = srcNamespace;
		this.dstNamespace = dstNamespace;
	}

	private Mappings(Mappings inverted) {
		this(inverted.dstNamespace, inverted.srcNamespace);

		this.inverted = inverted;
		inverted.inverted = this;
	}

	public MappingNamespace getSrcNamespace() {
		return srcNamespace;
	}

	public MappingNamespace getDstNamespace() {
		return dstNamespace;
	}

	public void setSrcNamespace(MappingNamespace namespace) {
		this.srcNamespace = Objects.requireNonNull(namespace);
	}

	public void setDstNamespace(MappingNamespace namespace) {
		this.dstNamespace = Objects.requireNonNull(namespace);
	}

	private ClassMapping findParent(String name, boolean orThrowException) {
		int i = name.lastIndexOf('$');

		if (i < 0) {
			return null;
		}

		String parentName = name.substring(0, i);
		ClassMapping parent = getClass(parentName);

		if (parent == null && orThrowException) {
			throw new IllegalStateException("unable to find parent class mapping " + parentName + " of class mapping " + name);
		}

		return parent;
	}

	public ClassMapping getClass(String name) {
		ClassMapping parent = findParent(name, false);
		return parent == null ? getTopLevelClass(name) : parent.getClass(name);
	}

	public ClassMapping getTopLevelClass(String name) {
		return classMappings.get(name);
	}

	public Collection<ClassMapping> getTopLevelClasses() {
		return classMappings.values();
	}

	public ClassMapping addClass(String src, String dst) {
		return addClass(new ClassMapping(this, src, dst));
	}

	private ClassMapping addClass(ClassMapping c) {
		ClassMapping parent = findParent(c.key(), true);

		if (parent == null) {
			return classMappings.compute(c.key(), (key, value) -> {
				return checkReplace(value, c);
			});
		}

		return parent.addClass(c);
	}

	public void removeClass(String name) {
		ClassMapping c = getClass(name);

		if (c != null) {
			if (c.parent == null) {
				classMappings.remove(name);
			} else {
				c.parent.removeClass(name);
			}
		}
	}

	public void validate() {
		for (ClassMapping c : classMappings.values()) {
			c.validate();
		}
	}

	public Mappings invert() {
		if (inverted == null) {
			inverted = new Mappings(this);

			for (ClassMapping c : classMappings.values()) {
				inverted.addClass(c.invert());
			}
		}

		return inverted;
	}

	public Mappings copy() {
		return copy(new Mappings(srcNamespace, dstNamespace));
	}

	protected Mappings copy(Mappings copy) {
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
			this.root = Objects.requireNonNull(root);

			if (inverted != null) {
				this.inverted = inverted;
				inverted.inverted = (T)this;
			}

			this.src = src;
			this.dst = dst;
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

		public final void reset() {
			set(src);
		}

		public final String getJavadoc() {
			return jav;
		}

		public final void setJavadoc(String jav) {
			this.jav = jav;
		}

		public boolean hasChildren() {
			return false;
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

		protected void validate() {

		}

		protected abstract T inverted();

		protected abstract T copy();

	}

	public static class ClassMapping extends Mapping<ClassMapping> {

		private final Map<String, ClassMapping> classMappings;
		private final Map<String, FieldMapping> fieldMappings;
		private final Map<String, MethodMapping> methodMappings;

		private ClassMapping parent;

		private ClassMapping(Mappings root, String src, String dst) {
			this(root, null, src, dst);
		}

		private ClassMapping(Mappings root, ClassMapping inverted, String src, String dst) {
			super(root, inverted, src, dst);

			this.classMappings = new LinkedHashMap<>();
			this.fieldMappings = new LinkedHashMap<>();
			this.methodMappings = new LinkedHashMap<>();
		}

		@Override
		public boolean hasChildren() {
			return !classMappings.isEmpty() || !fieldMappings.isEmpty() || !methodMappings.isEmpty();
		}

		@Override
		protected void validate() {
			String[] srcArgs = src.split("[$]");
			String[] dstArgs = dst.split("[$]");

			if (srcArgs.length != dstArgs.length) {
				throw new IllegalStateException("src and dst class names do not have the same nesting depth!");
			}
			if (srcArgs.length == 1) {
				return;
			}

			int i = src.lastIndexOf('$');
			int j = dst.lastIndexOf('$');
			String srcParentName = src.substring(0, i);
			String dstParentName = dst.substring(0, j);

			if (!parent.src.equals(srcParentName) || !parent.dst.equals(dstParentName)) {
				throw new IllegalStateException("class mapping " + this + " is not consistent with parent class mapping " + parent);
			}

			try {
				int srcIndex = Integer.parseInt(srcArgs[srcArgs.length - 1]);
				int dstIndex = Integer.parseInt(dstArgs[dstArgs.length - 1]);

				if (srcIndex != dstIndex) {
					throw new IllegalStateException("src and dst anonymous class indices do not match!");
				}
			} catch (NumberFormatException e) {

			}

			for (ClassMapping c : classMappings.values()) {
				c.validate();
			}
			for (FieldMapping f : fieldMappings.values()) {
				f.validate();
			}
			for (MethodMapping m : methodMappings.values()) {
				m.validate();
			}
		}

		@Override
		protected ClassMapping inverted() {
			inverted = new ClassMapping(root.inverted, this, dst, src);
			inverted.setJavadoc(jav);

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
			ClassMapping copy = new ClassMapping(root, src, dst);
			copy.setJavadoc(jav);

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

		public ClassMapping getParentClass() {
			return parent;
		}

		public ClassMapping getClass(String name) {
			return classMappings.get(name);
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

		public ClassMapping addClass(String src, String dst) {
			return addClass(new ClassMapping(root, src, dst));
		}
		
		private ClassMapping addClass(ClassMapping c) {
			c.parent = this;
			
			return classMappings.compute(c.key(), (key, value) -> {
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

		public void removeClass(String name) {
			classMappings.remove(name);
		}

		public void removeField(String name, String desc) {
			fieldMappings.remove(name + desc);
		}

		public void removeMethod(String name, String desc) {
			methodMappings.remove(name + desc);
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
			inverted = new FieldMapping(root.inverted, this, dst, src, MappingUtils.translateFieldDescriptor(desc, root));
			inverted.setJavadoc(jav);

			return inverted;
		}

		@Override
		protected FieldMapping copy() {
			FieldMapping copy = new FieldMapping(root, src, dst, desc);
			copy.setJavadoc(jav);

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
		public boolean hasChildren() {
			return !parameterMappings.isEmpty();
		}

		@Override
		protected String key() {
			return super.key() + desc;
		}

		@Override
		protected void validate() {
			for (ParameterMapping p : parameterMappings.values()) {
				p.validate();
			}
		}

		@Override
		protected MethodMapping inverted() {
			inverted = new MethodMapping(root.inverted, this, dst, src, MappingUtils.translateMethodDescriptor(desc, root));
			inverted.setJavadoc(jav);

			for (ParameterMapping p : parameterMappings.values()) {
				inverted.addParameter(p.inverted());
			}

			return inverted;
		}

		@Override
		protected MethodMapping copy() {
			MethodMapping copy = new MethodMapping(root, src, dst, desc);
			copy.setJavadoc(jav);

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
				int argAndRetSize = type.getArgumentsAndReturnSizes();

				parameterCount = argAndRetSize >> 2;
			}

			return parameterCount;
		}

		public ClassMapping getParent() {
			return parent;
		}

		public ParameterMapping getParameter(int index) {
			return parameterMappings.get(Integer.toString(index));
		}

		public Collection<ParameterMapping> getParameters() {
			return parameterMappings.values();
		}

		public ParameterMapping addParameter(String src, String dst, int index) {
			return addParameter(new ParameterMapping(root, src, dst, index));
		}

		private ParameterMapping addParameter(ParameterMapping p) {
			p.parent = this;

			return parameterMappings.compute(p.key(), (key, value) -> {
				return checkReplace(value, p);
			});
		}

		public void removeParameter(int index) {
			parameterMappings.remove(Integer.toString(index));
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
			inverted.setJavadoc(jav);

			return inverted;
		}

		@Override
		protected ParameterMapping copy() {
			ParameterMapping copy = new ParameterMapping(root, src, dst, index);
			copy.setJavadoc(jav);

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
