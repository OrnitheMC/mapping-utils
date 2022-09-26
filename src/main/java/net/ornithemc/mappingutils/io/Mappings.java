package net.ornithemc.mappingutils.io;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.objectweb.asm.Type;

import net.ornithemc.mappingutils.MappingUtils;

public class Mappings {

	private final Map<String, ClassMapping> classMappings;

	private MappingNamespace srcNamespace;
	private MappingNamespace dstNamespace;
	private MappingValidator validator;

	private Mappings inverted;

	public Mappings() {
		this(MappingNamespace.NONE, MappingNamespace.NONE);
	}

	public Mappings(MappingNamespace srcNamespace, MappingNamespace dstNamespace) {
		this.classMappings = new LinkedHashMap<>();

		this.srcNamespace = srcNamespace;
		this.dstNamespace = dstNamespace;
		this.validator = MappingValidator.ALWAYS;
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

	public void setValidator(MappingValidator validator) {
		this.validator = validator;
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

		return parent.addChild(c);
	}

	public ClassMapping removeClass(String key) {
		ClassMapping c = getClass(key);

		if (c != null) {
			if (c.parent == null) {
				classMappings.remove(key);
			} else {
				c.parent.removeChild(key);
			}
		}

		return c;
	}

	public void validate() {
		Iterator<ClassMapping> it = classMappings.values().iterator();

		while (it.hasNext()) {
			ClassMapping c = it.next();

			if (!c.validate()) {
				it.remove();
			}
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

		protected final Mappings root;
		protected final Map<String, Mapping<?>> children;

		protected T inverted;
		protected Mapping<?> parent;

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

			this.children = new LinkedHashMap<>();

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
			return parent;
		}

		public Mapping<?> getChild(MappingTarget target, String key) {
			return getChild(key);
		}

		@SuppressWarnings("unchecked")
		protected <M extends Mapping<M>> M getChild(String key) {
			return (M)children.get(key);
		}

		public Collection<Mapping<?>> getChildren() {
			return children.values();
		}

		@SuppressWarnings("unchecked")
		protected <M extends Mapping<M>> Collection<M> getChildren(MappingTarget target) {
			List<M> c = new LinkedList<>();

			for (Mapping<?> m : children.values()) {
				if (m.target() == target) {
					c.add((M)m);
				}
			}

			return c;
		}

		public boolean hasChildren() {
			return !children.isEmpty();
		}

		public Mapping<?> addChild(MappingTarget target, String key, String dst) {
			switch (target) {
			case CLASS:
				return addChild(new ClassMapping(root, key, dst));
			case FIELD:
				return addChild(new FieldMapping(root, key, dst));
			case METHOD:
				return addChild(new MethodMapping(root, key, dst));
			case PARAMETER:
				return addChild(new ParameterMapping(root, key, dst));
			default:
				throw new IllegalStateException("cannot add child mapping of target " + target);
			}
		}

		@SuppressWarnings("unchecked")
		protected <M extends Mapping<M>> M addChild(M m) {
			m.parent = this;

			return (M)children.compute(m.key(), (key, value) -> {
				return checkReplace((M)value, m);
			});
		}

		public Mapping<?> removeChild(MappingTarget target, String key) {
			return removeChild(key);
		}

		@SuppressWarnings("unchecked")
		protected <M extends Mapping<M>> M removeChild(String key) {
			return (M)children.remove(key);
		}

		public final T invert() {
			root.invert();

			if (inverted == null) {
				inverted = inverted();

				inverted.jav = jav;

				for (Mapping<?> m : children.values()) {
					inverted.addChild(m.invert());
				}
			}

			return inverted;
		}

		protected abstract T inverted();

		protected boolean validate() {
			Iterator<Mapping<?>> it = children.values().iterator();

			while (it.hasNext()) {
				Mapping<?> m = it.next();

				if (!m.validate()) {
					it.remove();
				}
			}

			return root.validator.validate(this);
		}

		protected T copy() {
			T copy = copied();

			copy.jav = jav;

			for (Mapping<?> m : children.values()) {
				copy.addChild(m.copy());
			}

			return copy;
		}

		protected abstract T copied();

	}

	public static class ClassMapping extends Mapping<ClassMapping> {

		private ClassMapping(Mappings root, String src, String dst) {
			this(root, null, src, dst);
		}

		private ClassMapping(Mappings root, ClassMapping inverted, String src, String dst) {
			super(root, inverted, src, dst);
		}

		@Override
		public MappingTarget target() {
			return MappingTarget.CLASS;
		}

		@Override
		public ClassMapping getParent() {
			return (ClassMapping)parent;
		}

		@Override
		protected ClassMapping inverted() {
			return new ClassMapping(root.inverted, this, dst, src);
		}

		@Override
		protected boolean validate() {
			String[] srcArgs = src.split("[$]");
			String[] dstArgs = dst.split("[$]");

			if (srcArgs.length != dstArgs.length) {
				throw new IllegalStateException("src and dst class names do not have the same nesting depth!");
			}
			if (srcArgs.length == 1) {
				return super.validate();
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

			return super.validate();
		}

		@Override
		protected ClassMapping copied() {
			return new ClassMapping(root, src, dst);
		}

		public ClassMapping getClass(String key) {
			return getChild(key);
		}

		public FieldMapping getField(String name, String desc) {
			return getField(FieldMapping.key(name, desc));
		}

		public FieldMapping getField(String key) {
			return getChild(key);
		}

		public MethodMapping getMethod(String name, String desc) {
			return getMethod(MethodMapping.key(name, desc));
		}

		public MethodMapping getMethod(String key) {
			return getChild(key);
		}

		public Collection<ClassMapping> getClasses() {
			return getChildren(MappingTarget.CLASS);
		}

		public Collection<FieldMapping> getFields() {
			return getChildren(MappingTarget.FIELD);
		}

		public Collection<MethodMapping> getMethods() {
			return getChildren(MappingTarget.METHOD);
		}

		public ClassMapping addClass(String src, String dst) {
			return addChild(new ClassMapping(root, src, dst));
		}

		public FieldMapping addField(String src, String dst, String desc) {
			return addField(FieldMapping.key(src, desc), dst);
		}

		private FieldMapping addField(String key, String dst) {
			return addChild(new FieldMapping(root, key, dst));
		}

		public MethodMapping addMethod(String src, String dst, String desc) {
			return addMethod(MethodMapping.key(src, desc), dst);
		}

		private MethodMapping addMethod(String key, String desc) {
			return addChild(new MethodMapping(root, key, desc));
		}

		public ClassMapping removeClass(String key) {
			return removeChild(key);
		}

		public FieldMapping removeField(String name, String desc) {
			return removeField(FieldMapping.key(name, desc));
		}

		public FieldMapping removeField(String key) {
			return removeChild(key);
		}

		public MethodMapping removeMethod(String name, String desc) {
			return removeMethod(MethodMapping.key(name, desc));
		}

		public MethodMapping removeMethod(String key) {
			return removeChild(key);
		}
	}

	public static class FieldMapping extends Mapping<FieldMapping> {

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
			return (ClassMapping)parent;
		}

		@Override
		protected FieldMapping inverted() {
			return new FieldMapping(root.inverted, this, dst, src, MappingUtils.translateFieldDescriptor(desc, root));
		}

		@Override
		protected FieldMapping copied() {
			return new FieldMapping(root, null, src, dst, desc);
		}

		public String getDesc() {
			return desc;
		}
	}

	public static class MethodMapping extends Mapping<MethodMapping> {

		private final ParameterMapping[] parameters;

		private String desc;

		private MethodMapping(Mappings root, String key, String dst) {
			this(root, null, key.split("[:]")[0], dst, key.split("[:]")[1]);
		}

		private MethodMapping(Mappings root, MethodMapping inverted, String src, String dst, String desc) {
			super(root, inverted, src, dst);

			this.parameters = new ParameterMapping[parameterCount(desc)];

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
			return (ClassMapping)parent;
		}

		@Override
		protected MethodMapping inverted() {
			return new MethodMapping(root.inverted, this, dst, src, MappingUtils.translateMethodDescriptor(desc, root));
		}

		@Override
		protected MethodMapping copied() {
			return new MethodMapping(root, null, src, dst, desc);
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
			return getChild(key);
		}

		public Collection<ParameterMapping> getParameters() {
			return getChildren(MappingTarget.PARAMETER);
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

			return addChild(p);
		}

		public ParameterMapping removeParameter(int index) {
			ParameterMapping p = parameters[index];

			if (p != null) {
				parameters[p.index] = null;
				removeChild(p.key());
			}

			return p;
		}

		public ParameterMapping removeParameter(String key) {
			ParameterMapping p = getChild(key);

			if (p != null) {
				parameters[p.index] = null;
				removeChild(p.key());
			}

			return p;
		}
	}

	public static class ParameterMapping extends Mapping<ParameterMapping> {

		private final int index;

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
			return (MethodMapping)parent;
		}

		@Override
		protected ParameterMapping inverted() {
			return new ParameterMapping(root.inverted, this, dst, src, index);
		}

		@Override
		protected ParameterMapping copied() {
			return new ParameterMapping(root, null, src, dst, index);
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
