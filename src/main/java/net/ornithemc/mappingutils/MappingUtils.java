package net.ornithemc.mappingutils;

import java.nio.file.Path;

import org.objectweb.asm.Type;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.matcher.Matches;
import net.ornithemc.mappingutils.io.matcher.MatchesReader;
import net.ornithemc.mappingutils.io.tiny.TinyMappings;
import net.ornithemc.mappingutils.io.tiny.TinyReader;
import net.ornithemc.mappingutils.io.tinyv2.TinyV2Mappings;
import net.ornithemc.mappingutils.io.tinyv2.TinyV2Reader;
import net.ornithemc.mappingutils.io.tinyv2.TinyV2Writer;

public class MappingUtils {

	public static Mappings concatenate(Mappings... m) {
		return MappingConcatenator.run(m);
	}

	public static void updateMappingsV2WithCalamusV1(Path srcPath, Path dstPath, Path calamusSrcPath, Path calamusDstPath, Path matchesPath) throws Exception {
		TinyV2Mappings src = TinyV2Reader.read(srcPath);
		TinyV2Mappings dst = new TinyV2Mappings(src.getSrcNamespace(), src.getDstNamespace());
		TinyMappings calamusSrc = TinyReader.read(calamusSrcPath);
		TinyMappings calamusDst = TinyReader.read(calamusDstPath);
		Matches matches = MatchesReader.read(matchesPath);

		MappingUpdater.run(src, dst, calamusSrc, calamusDst, matches);
		TinyV2Writer.write(dstPath, dst);
	}

	public static String updateFieldDescriptor(String desc, Mappings mappings) {
		Type type = Type.getType(desc);
		type = updateType(type, mappings);

		return type.getDescriptor();
	}

	public static String updateMethodDescriptor(String desc, Mappings mappings) {
		Type type = Type.getMethodType(desc);

		Type[] argTypes = type.getArgumentTypes();
		Type returnType = type.getReturnType();

		for (int i = 0; i < argTypes.length; i++) {
			argTypes[i] = updateType(argTypes[i], mappings);
		}
		returnType = updateType(returnType, mappings);

		type = Type.getMethodType(returnType, argTypes);

		return type.getDescriptor();
	}

	public static Type updateType(Type type, Mappings mappings) {
		switch (type.getSort()) {
		case Type.OBJECT:
			String className = type.getInternalName();
			ClassMapping mapping = mappings.getClass(className);

			if (mapping != null) {
				className = mapping.get();
				type = Type.getObjectType(className);
			}

			break;
		case Type.ARRAY:
			Type elementType = type.getElementType();
			elementType = updateType(elementType, mappings);

			int numDim = type.getDimensions();
			String desc = "";

			for (int i = 0; i < numDim; i++) {
				desc += "[";
			}

			desc += elementType.getDescriptor();
			type = Type.getType(desc);

			break;
		}

		return type;
	}
	
	public static void main(String[] args) {

	}
}
