package net.ornithemc.mappingutils;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.Mappings.ParameterMapping;

public interface Mapper {

	public static final Mapper IDENTITY = new Mapper() {

		@Override
		public String mapClass(String className) {
			return className;
		}

		@Override
		public String mapField(String className, String fieldName, String fieldDesc) {
			return fieldName;
		}

		@Override
		public String mapMethod(String className, String methodName, String methodDesc) {
			return methodName;
		}

		@Override
		public String mapParameter(String className, String methodName, String methodDesc, String parameterName, int index) {
			return parameterName;
		}
	};

	public static Mapper of(Mappings mappings) {
		return new Mapper() {

			@Override
			public String mapClass(String className) {
				ClassMapping c = mappings.getClass(className);
				return c == null || c.get().isEmpty() ? className : c.getComplete();
			}

			@Override
			public String mapField(String className, String fieldName, String fieldDesc) {
				ClassMapping c = mappings.getClass(className);
				FieldMapping f = (c == null) ? null : c.getField(fieldName, fieldDesc);
				return f == null || f.get().isEmpty() ? fieldName : f.get();
			}

			@Override
			public String mapMethod(String className, String methodName, String methodDesc) {
				ClassMapping c = mappings.getClass(className);
				MethodMapping m = (c == null) ? null : c.getMethod(methodName, methodDesc);
				return m == null || m.get().isEmpty() ? methodName : m.get();
			}

			@Override
			public String mapParameter(String className, String methodName, String methodDesc, String parameterName, int index) {
				ClassMapping c = mappings.getClass(className);
				MethodMapping m = (c == null) ? null : c.getMethod(methodName, methodDesc);
				ParameterMapping p = (m == null) ? null : m.getParameter(index);
				return p == null || p.get().isEmpty() ? parameterName : p.get();
			}
		};
	}

	default void apply(Mappings mappings) {
		apply(this, mappings);
	}

	public static void apply(Mapper mapper, Mappings mappings) {
		for (Mapping<?> mapping : mappings.getTopLevelClasses()) {
			apply(mapper, mapping);
		}
	}

	public static void apply(Mapper mapper, Mapping<?> mapping) {
		for (Mapping<?> child : mapping.getChildren()) {
			apply(mapper, child);
		}

		switch (mapping.target()) {
		case CLASS:
			ClassMapping c = (ClassMapping)mapping;
			c.set(mapper.mapClass(c.src(), c.get()));

			break;
		case FIELD:
			FieldMapping f = (FieldMapping)mapping;
			f.set(mapper.mapField(f.getParent().src(), f.src(), f.getDesc(), f.get()));

			break;
		case METHOD:
			MethodMapping m = (MethodMapping)mapping;
			m.set(mapper.mapMethod(m.getParent().src(), m.src(), m.getDesc(), m.get()));

			break;
		case PARAMETER:
			ParameterMapping p = (ParameterMapping)mapping;
			MethodMapping pm = p.getParent();
			p.set(mapper.mapParameter(pm.getParent().src(), pm.src(), pm.getDesc(), p.src(), p.getIndex(), p.get()));

			break;
		default:
			throw new IllegalStateException("unknown mapping target " + mapping.target());
		}
	}

	default String mapClass(String className, String mapping) {
		return mapClass(className);
	}

	String mapClass(String className);

	default String mapField(String className, String fieldName, String fieldDesc, String mapping) {
		return mapField(className, fieldName, fieldDesc);
	}

	String mapField(String className, String fieldName, String fieldDesc);

	default String mapMethod(String className, String methodName, String methodDesc, String mapping) {
		return mapMethod(className, methodName, methodDesc);
	}

	String mapMethod(String className, String methodName, String methodDesc);

	default String mapParameter(String className, String methodName, String methodDesc, String parameterName, int index, String mapping) {
		return mapParameter(className, methodName, methodDesc, parameterName, index);
	}

	String mapParameter(String className, String methodName, String methodDesc, String parameterName, int index);

}
