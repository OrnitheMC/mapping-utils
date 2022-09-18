package net.ornithemc.mappingutils;

import java.nio.file.Path;

import org.objectweb.asm.Type;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.matcher.Matches;
import net.ornithemc.mappingutils.io.matcher.MatchesReader;
import net.ornithemc.mappingutils.io.tiny.v1.TinyV1Mappings;
import net.ornithemc.mappingutils.io.tiny.v1.TinyV1Reader;
import net.ornithemc.mappingutils.io.tiny.v2.TinyV2Mappings;
import net.ornithemc.mappingutils.io.tiny.v2.TinyV2Reader;
import net.ornithemc.mappingutils.io.tiny.v2.TinyV2Writer;

public class MappingUtils {

	public static void updateMappingsV2WithCalamusV1(Path srcPath, Path dstPath, Path calamusSrcPath, Path calamusDstPath, Path matchesPath) throws Exception {
		TinyV2Mappings src = TinyV2Reader.read(srcPath);
		TinyV2Mappings dst = new TinyV2Mappings(src.getSrcNamespace(), src.getDstNamespace());
		TinyV1Mappings calamusSrc = TinyV1Reader.read(calamusSrcPath);
		TinyV1Mappings calamusDst = TinyV1Reader.read(calamusDstPath);
		Matches matches = MatchesReader.read(matchesPath);

		MappingUpdater.run(src, dst, calamusSrc, calamusDst, matches);
		TinyV2Writer.write(dstPath, dst);
	}

	public static String translateFieldDescriptor(String desc, Mappings mappings) {
		Type type = Type.getType(desc);
		type = translateType(type, mappings);

		return type.getDescriptor();
	}

	public static String translateMethodDescriptor(String desc, Mappings mappings) {
		Type type = Type.getMethodType(desc);

		Type[] argTypes = type.getArgumentTypes();
		Type returnType = type.getReturnType();

		for (int i = 0; i < argTypes.length; i++) {
			argTypes[i] = translateType(argTypes[i], mappings);
		}
		returnType = translateType(returnType, mappings);

		type = Type.getMethodType(returnType, argTypes);

		return type.getDescriptor();
	}

	public static Type translateType(Type type, Mappings mappings) {
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
			elementType = translateType(elementType, mappings);

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
