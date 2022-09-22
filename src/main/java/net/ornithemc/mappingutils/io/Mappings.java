package net.ornithemc.mappingutils.io;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
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

	private ClassMapping findParent(String key, boolean orThrowException) {
		int i = key.lastIndexOf('$');

		if (i < 0) {
			return null;
		}

		String parentKey = key.substring(0, i);
		ClassMapping parent = getClass(parentKey);

		if (parent == null && orThrowException) {
			throw new IllegalStateException("unable to find parent class mapping " + parentKey + " of class mapping " + key);
		}

		return parent;
	}

	public ClassMapping getClass(String key) {
		ClassMapping parent = findParent(key, false);
		return parent == null ? getTopLevelClass(key) : parent.getClass(key);
	}

	public ClassMapping getTopLevelClass(String key) {
		return classMappings.get(key);
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

	public ClassMapping removeClass(String key) {
		ClassMapping c = getClass(key);

		if (c != null) {
			if (c.parent == null) {
				classMappings.remove(key);
			} else {
				c.parent.removeClass(key);
			}
		}

		return c;
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

		public abstract MappingTarget target();

		public String key() {
			return src;
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

		public Mapping<?> getParent() {
			return null;
		}

		public Mapping<?> getChild(MappingTarget target, String key) {
			throw new IllegalStateException("cannot get child mapping of target " + target);
		}

		public Mapping<?> addChild(MappingTarget target, String key, String dst) {
			throw new IllegalStateException("cannot add child mapping of target " + target);
		}

		public Mapping<?> removeChild(MappingTarget target, String key) {
			throw new IllegalStateException("cannot remove child mapping of target " + target);
		}

		public Collection<Mapping<?>> getChildren() {
			return Collections.emptyList();
		}

		public boolean hasChildren() {
			return false;
		}

		public final T invert() {
			root.invert();

			if (inverted == null) {
				inverted = inverted();
			}

			return inverted;
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
		public MappingTarget target() {
			return MappingTarget.CLASS;
		}

		@Override
		public ClassMapping getParent() {
			return parent;
		}

		@Override
		public Mapping<?> getChild(MappingTarget target, String key) {
			switch (target) {
			case CLASS:
				return getClass(key);
			case FIELD:
				return getField(key);
			case METHOD:
				return getMethod(key);
			default:
				return super.getChild(target, key);
			}
		}

		@Override
		public Mapping<?> addChild(MappingTarget target, String key, String dst) {
			switch (target) {
			case CLASS:
				return addClass(key, dst);
			case FIELD:
				return addField(key, dst);
			case METHOD:
				return addMethod(key, dst);
			default:
				return super.addChild(target, key, dst);
			}
		}

		@Override
		public Mapping<?> removeChild(MappingTarget target, String key) {
			switch (target) {
			case CLASS:
				return removeClass(key);
			case FIELD:
				return removeField(key);
			case METHOD:
				return removeMethod(key);
			default:
				return super.removeChild(target, key);
			}
		}

		@Override
		public Collection<Mapping<?>> getChildren() {
			Collection<Mapping<?>> children = new LinkedList<>();

			children.addAll(classMappings.values());
			children.addAll(fieldMappings.values());
			children.addAll(methodMappings.values());

			return children;
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

		public ClassMapping getClass(String key) {
			return classMappings.get(key);
		}

		public FieldMapping getField(String name, String desc) {
			return getField(FieldMapping.key(name, desc));
		}

		public FieldMapping getField(String key) {
			return fieldMappings.get(key);
		}

		public MethodMapping getMethod(String name, String desc) {
			return getMethod(MethodMapping.key(name, desc));
		}

		public MethodMapping getMethod(String key) {
			return methodMappings.get(key);
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
			return addField(FieldMapping.key(src, desc), dst);
		}

		private FieldMapping addField(String key, String dst) {
			return addField(new FieldMapping(root, key, dst));
		}

		private FieldMapping addField(FieldMapping f) {
			f.parent = this;

			return fieldMappings.compute(f.key(), (key, value) -> {
				return checkReplace(value, f);
			});
		}

		public MethodMapping addMethod(String src, String dst, String desc) {
			return addMethod(MethodMapping.key(src, desc), dst);
		}

		private MethodMapping addMethod(String key, String desc) {
			return addMethod(new MethodMapping(root, key, desc));
		}

		private MethodMapping addMethod(MethodMapping m) {
			m.parent = this;

			return methodMappings.compute(m.key(), (key, value) -> {
				return checkReplace(value, m);
			});
		}

		public ClassMapping removeClass(String key) {
			return classMappings.remove(key);
		}

		public FieldMapping removeField(String name, String desc) {
			return removeField(FieldMapping.key(name, desc));
		}

		public FieldMapping removeField(String key) {
			return fieldMappings.remove(key);
		}

		public MethodMapping removeMethod(String name, String desc) {
			return removeMethod(MethodMapping.key(name, desc));
		}

		public MethodMapping removeMethod(String key) {
			return methodMappings.remove(key);
		}
	}

	public static class FieldMapping extends Mapping<FieldMapping> {

		private ClassMapping parent;
		private String desc;

		private FieldMapping(Mappings root, String key, String dst) {
			this(root, null, key.split("[:]")[0], dst, key.split("[:]")[1]);
		}

		private FieldMapping(Mappings root, FieldMapping inverted, String src, String dst, String desc) {
			super(root, inverted, src, dst);

			this.desc = desc;
		}

		private static String key(String name, String desc) {
			return name + ":" + desc;
		}

		@Override
		public MappingTarget target() {
			return MappingTarget.FIELD;
		}

		@Override
		public String key() {
			return key(src, desc);
		}

		@Override
		public ClassMapping getParent() {
			return parent;
		}

		@Override
		protected FieldMapping inverted() {
			inverted = new FieldMapping(root.inverted, this, dst, src, MappingUtils.translateFieldDescriptor(desc, root));
			inverted.setJavadoc(jav);

			return inverted;
		}

		@Override
		protected FieldMapping copy() {
			FieldMapping copy = new FieldMapping(root, null, src, dst, desc);
			copy.setJavadoc(jav);

			return copy;
		}

		public String getDesc() {
			return desc;
		}
	}

	public static class MethodMapping extends Mapping<MethodMapping> {

		private final ParameterMapping[] parameters;
		private final Map<String, ParameterMapping> parameterMappings;

		private ClassMapping parent;
		private String desc;

		private MethodMapping(Mappings root, String key, String dst) {
			this(root, null, key.split("[:]")[0], dst, key.split("[:]")[1]);
		}

		private MethodMapping(Mappings root, MethodMapping inverted, String src, String dst, String desc) {
			super(root, inverted, src, dst);

			this.parameters = new ParameterMapping[parameterCount(desc)];
			this.parameterMappings = new LinkedHashMap<>();

			this.desc = desc;
		}

		private static String key(String name, String desc) {
			return name + ":" + desc;
		}

		private static int parameterCount(String desc) {
			Type type = Type.getMethodType(desc);
			int argAndRetSize = type.getArgumentsAndReturnSizes();

			return argAndRetSize >> 2;
		}

		@Override
		public MappingTarget target() {
			return MappingTarget.METHOD;
		}

		@Override
		public String key() {
			return key(src, desc);
		}

		@Override
		public ClassMapping getParent() {
			return parent;
		}

		@Override
		public Mapping<?> getChild(MappingTarget target, String key) {
			if (target == MappingTarget.PARAMETER) {
				return getParameter(key);
			} else {
				return super.getChild(target, key);
			}
		}

		@Override
		public Mapping<?> addChild(MappingTarget target, String key, String dst) {
			if (target == MappingTarget.PARAMETER) {
				return addParameter(key, dst);
			} else {
				return super.addChild(target, key, dst);
			}
		}

		@Override
		public Mapping<?> removeChild(MappingTarget target, String key) {
			if (target == MappingTarget.PARAMETER) {
				return removeParameter(key);
			} else {
				return super.removeChild(target, key);
			}
		}

		@Override
		public Collection<Mapping<?>> getChildren() {
			Collection<Mapping<?>> children = new LinkedList<>();

			children.addAll(parameterMappings.values());

			return children;
		}

		@Override
		public boolean hasChildren() {
			return !parameterMappings.isEmpty();
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
			MethodMapping copy = new MethodMapping(root, null, src, dst, desc);
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
			return parameters.length;
		}

		public ParameterMapping getParameter(int index) {
			return parameters[index];
		}

		public ParameterMapping getParameter(String key) {
			return parameterMappings.get(key);
		}

		public Collection<ParameterMapping> getParameters() {
			return parameterMappings.values();
		}

		public ParameterMapping addParameter(String name, String dst, int index) {
			return addParameter(ParameterMapping.key(index, name), dst);
		}

		public ParameterMapping addParameter(String key, String dst) {
			return addParameter(new ParameterMapping(root, key, dst));
		}

		private ParameterMapping addParameter(ParameterMapping p) {
			if (p.index >= parameters.length) {
				System.out.println(this + " ignoring illegal parameter mapping " + p + ": index out of bounds");
				return null;
			}

			p.parent = this;

			parameters[p.index] = p;
			return parameterMappings.compute(p.key(), (key, value) -> {
				return checkReplace(value, p);
			});
		}

		public ParameterMapping removeParameter(int index) {
			ParameterMapping p = parameters[index];

			if (p != null) {
				parameters[p.index] = null;
				parameterMappings.remove(p.key());
			}

			return p;
		}

		public ParameterMapping removeParameter(String key) {
			ParameterMapping p = parameterMappings.get(key);

			if (p != null) {
				parameters[p.index] = null;
				parameterMappings.remove(p.key());
			}

			return p;
		}
	}

	public static class ParameterMapping extends Mapping<ParameterMapping> {

		private final int index;

		private MethodMapping parent;

		private ParameterMapping(Mappings root, String key, String dst) {
			this(root, null, key.substring(key.indexOf(':') + 1), dst, Integer.parseInt(key.substring(0, key.indexOf(':'))));
		}

		private ParameterMapping(Mappings root, ParameterMapping inverted, String src, String dst, int index) {
			super(root, inverted, src, dst);

			if (index < 0) {
				throw new IllegalArgumentException("parameter index cannot be negative!");
			}

			this.index = index;
		}

		private static String key(int index, String name) {
			return Integer.toString(index) + ":" + name;
		}

		@Override
		public MappingTarget target() {
			return MappingTarget.PARAMETER;
		}

		@Override
		public String key() {
			return key(index, src);
		}

		@Override
		public MethodMapping getParent() {
			return parent;
		}

		@Override
		protected ParameterMapping inverted() {
			inverted = new ParameterMapping(root.inverted, this, dst, src, index);
			inverted.setJavadoc(jav);

			return inverted;
		}

		@Override
		protected ParameterMapping copy() {
			ParameterMapping copy = new ParameterMapping(root, null, src, dst, index);
			copy.setJavadoc(jav);

			return copy;
		}

		public int getIndex() {
			return index;
		}
	}

	private static <T extends Mapping<T>> T checkReplace(T o, T n) {
		if (o != null && n != null) {
			System.err.println("replacing mapping " + o + " with " + n);
		}

		return n;
	}
}
