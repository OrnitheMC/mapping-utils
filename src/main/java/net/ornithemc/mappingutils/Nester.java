package net.ornithemc.mappingutils;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.objectweb.asm.commons.Remapper;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.Mappings.ParameterMapping;

import net.ornithemc.nester.nest.Nest;
import net.ornithemc.nester.nest.Nests;

class Nester {

	static Mappings run(Mappings mappings, Nests nests, boolean apply) {
		return new Nester(mappings, nests, apply).run();
	}

	private final Mappings src;
	private final Mappings dst;
	private final Nests nests;
	private final Nests mappedNests;
	private final boolean apply;

	private final Mapper translator;
	private final Mapper mappedTranslator;

	private Nester(Mappings mappings, Nests nests, boolean apply) {
		this.src = mappings;
		this.dst = new Mappings();
		this.nests = nests;
		this.mappedNests = apply ? MappingUtils.mapNests(this.nests, this.src) : Nests.empty();
		this.apply = apply;

		this.translator = buildMapper(this.nests, this.apply);
		this.mappedTranslator = buildMapper(this.mappedNests, this.apply);
	}

	private static Map<String, String> buildTranslations(Nests nests, boolean apply) {
		Map<String, String> translations = new HashMap<>();

		for (Nest nest : nests) {
			translations.put(nest.className, buildTranslation(nests, nest.className, translations));
		}

		if (!apply) {
			Map<String, String> inverted = new HashMap<>(translations);
			translations.clear();

			for (Entry<String, String> entry : inverted.entrySet()) {
				translations.put(entry.getValue(), entry.getKey());
			}
		}

		return translations;
	}

	private static String buildTranslation(Nests nests, String className, Map<String, String> translations) {
		String translation = translations.get(className);

		if (translation != null) {
			return translation;
		}

		Nest nest = nests.get(className);

		if (nest == null) {
			return className;
		}

		return buildTranslation(nests, nest.enclClassName, translations) + "$" + nest.innerName;
	}

	public static Mapper buildMapper(Nests nests, boolean apply) {
		Map<String, String> translations = buildTranslations(nests, apply);

		return new Mapper() {

			@Override
			public String mapClass(String className) {
				return translations.getOrDefault(className, className);
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
	}

	public static Remapper buildRemapper(Nests nests, boolean apply) {
		Map<String, String> translations = buildTranslations(nests, apply);

		return new Remapper() {

			@Override
			public String map(String key) {
				return translations.getOrDefault(key, key);
			}
		};
	}

	public Mappings run() {
		dst.setSrcNamespace(src.getSrcNamespace());
		dst.setDstNamespace(src.getDstNamespace());

		for (Mapping m : src.getTopLevelClasses()) {
			translate(null, null, m);
		}

		return dst;
	}

	private void translate(Mapping p, Mapping tp, Mapping m) {
		if (m == null) {
			return;
		}

		Mapping tm = null;

		String name;
		String desc;
		String mapping;

		switch (m.target()) {
		case CLASS:
			ClassMapping cm = (ClassMapping)m;

			name = cm.src();
			mapping = cm.getComplete();

			String translatedName = translator.mapClass(name);
			String translatedMapping = mappedTranslator.mapClass(mapping);

			// we get the nests by a class' un-nested names
			Nest nest = nests.get(apply ? name : translatedName);
			Nest mappedNest = mappedNests.get(apply ? mapping : translatedMapping);

			if (nest != null) {
				if (apply) {
					if (p == null) {
						// enclosing class may not have been translated yet
						// since that depends on the order in which the mappings
						// are read
						// it could also be that the enclosing class does not exist
						// (since nester is able to generate those) in which case
						// we need to create a dummy mapping
						String enclName = nest.enclClassName;
						String mappedEnclName = mappedNest.enclClassName;
						String translatedEnclName = translator.mapClass(enclName);
						String mappedTranslatedEnclName = mappedTranslator.mapClass(mappedEnclName);

						tp = dst.getClass(translatedEnclName);

						if (tp == null) {
							p = src.getClass(enclName);

							if (p == null) {
								tp = dst.addClass(translatedEnclName, mappedTranslatedEnclName);
							} else {
								// make sure the entire hierarchy is added
								translate(null, null, p);
							}
						}
					}
				} else {
					translatedMapping = translatedMapping.replace("$", "__");
				}
			}

			tm = dst.getClass(translatedName);

			if (tm == null) {
				tm = dst.addClass(translatedName, translatedName);
			}

			tm.set(ClassMapping.getSimplified(translatedMapping));

			break;
		case FIELD:
			if (p == null) {
				throw new IllegalStateException("no parent mapping for field " + m);
			}

			FieldMapping fm = (FieldMapping)m;

			name = fm.src();
			desc = MappingUtils.translateFieldDescriptor(fm.getDesc(), translator);
			mapping = fm.get();

			tm = tp.getField(name, desc);

			if (tm == null) {
				tm = tp.addField(name, name, desc);
			}

			tm.set(mapping);

			break;
		case METHOD:
			if (p == null) {
				throw new IllegalStateException("no parent mapping for method " + m);
			}

			MethodMapping mm = (MethodMapping)m;

			name = mm.src();
			desc = MappingUtils.translateMethodDescriptor(mm.getDesc(), translator);
			mapping = mm.get();

			tm = tp.getMethod(name, desc);

			if (tm == null) {
				tm = tp.addMethod(name, name, desc);
			}

			tm.set(mapping);

			break;
		case PARAMETER:
			if (p == null) {
				throw new IllegalStateException("no parent mapping for parameter " + m);
			}

			ParameterMapping pm = (ParameterMapping)m;

			name = pm.src();
			int index = pm.getIndex();
			mapping = pm.get();

			tm = tp.getParameter(name, index);

			if (tm == null) {
				tm = tp.addParameter(name, name, index);
			}

			tm.set(mapping);

			break;
		default:
			throw new IllegalStateException("unknown mapping target " + m.target());
		}

		tm.setJavadoc(m.getJavadoc());

		for (Mapping c : m.getChildren()) {
			translate(m, tm, c);
		}
	}
}
