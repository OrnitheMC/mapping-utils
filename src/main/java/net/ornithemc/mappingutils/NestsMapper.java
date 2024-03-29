package net.ornithemc.mappingutils;

import net.ornithemc.nester.nest.Nest;
import net.ornithemc.nester.nest.NestType;
import net.ornithemc.nester.nest.Nests;

class NestsMapper {

	static Nests run(Nests nests, Mapper mapper) {
		return new NestsMapper(nests, mapper).run();
	}

	private final Nests src;
	private final Nests dst;
	private final Mapper mapper;

	private NestsMapper(Nests nests, Mapper mapper) {
		this.src = nests;
		this.dst = Nests.empty();
		this.mapper = mapper;
	}

	public Nests run() {
		for (Nest nest : src) {
			dst.add(map(nest));
		}

		return dst;
	}

	private Nest map(Nest nest) {
		NestType type = nest.type;
		String className = mapClassName(nest.className);
		String enclClassName = mapOuterName(nest.className, nest.enclClassName);
		String enclMethodName = (nest.enclMethodName == null) ? null : mapMethodName(nest.enclClassName, nest.enclMethodName, nest.enclMethodDesc);
		String enclMethodDesc = (nest.enclMethodDesc == null) ? null : mapMethodDesc(nest.enclClassName, nest.enclMethodName, nest.enclMethodDesc);
		String innerName = mapInnerName(nest.className, nest.innerName);
		int access = nest.access;

		return new Nest(type, className, enclClassName, enclMethodName, enclMethodDesc, innerName, access);
	}

	private String mapClassName(String name) {
		return mapper.mapClass(name);
	}

	private String mapMethodName(String className, String name, String desc) {
		return mapper.mapMethod(className, name, desc);
	}

	private String mapMethodDesc(String className, String name, String desc) {
		return MappingUtils.translateMethodDescriptor(desc, mapper);
	}

	private String mapOuterName(String name, String enclClassName) {
		String mappedName = mapClassName(name);
		int idx = mappedName.lastIndexOf("__");

		if (idx > 0) {
			// provided mappings already apply nesting
			return mappedName.substring(0, idx);
		}

		return mapClassName(enclClassName);
	}

	private String mapInnerName(String name, String innerName) {
		String mappedName = mapClassName(name);
		int idx = mappedName.lastIndexOf("__");

		if (idx > 0) {
			// provided mappings already apply nesting
			return mappedName.substring(idx + 2);
		}

		int i = 0;
		while (i < innerName.length() && Character.isDigit(innerName.charAt(i))) {
			i++;
		}
		if (i < innerName.length()) {
			// local classes have a number prefix
			String prefix = innerName.substring(0, i);
			String simpleName = innerName.substring(i);

			// make sure the class does not have custom inner name
			if (name.endsWith(simpleName)) {
				// inner name is full name with package stripped
				// so translate that
				innerName = prefix + mappedName.substring(mappedName.lastIndexOf('/') + 1);
			}
		} else {
			// anonymous class
			String simpleName = mappedName.substring(mappedName.lastIndexOf('/') + 1);

			if (simpleName.startsWith("C_")) {
				// mapped name is Calamus intermediary format C_<number>
				// we strip the C_ prefix and keep the number as the inner name
				return simpleName.substring(2);
			} else {
				// keep the inner name given by the nests file
				return innerName;
			}
		}

		return innerName;
	}
}
