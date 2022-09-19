package net.ornithemc.mappingutils;

import java.nio.file.Path;

import org.objectweb.asm.Type;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.diff.tiny.v1.TinyV1Diff;
import net.ornithemc.mappingutils.io.diff.tiny.v1.TinyV1DiffReader;
import net.ornithemc.mappingutils.io.diff.tiny.v1.TinyV1DiffWriter;
import net.ornithemc.mappingutils.io.diff.tiny.v2.TinyV2Diff;
import net.ornithemc.mappingutils.io.diff.tiny.v2.TinyV2DiffReader;
import net.ornithemc.mappingutils.io.diff.tiny.v2.TinyV2DiffWriter;
import net.ornithemc.mappingutils.io.matcher.Matches;
import net.ornithemc.mappingutils.io.matcher.MatchesReader;
import net.ornithemc.mappingutils.io.tiny.v1.TinyV1Mappings;
import net.ornithemc.mappingutils.io.tiny.v1.TinyV1Reader;
import net.ornithemc.mappingutils.io.tiny.v1.TinyV1Writer;
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

	public static void diffTinyV1Mappings(Path pathA, Path pathB, Path diffPath) throws Exception {
		TinyV1Mappings a = TinyV1Reader.read(pathA);
		TinyV1Mappings b = TinyV1Reader.read(pathB);
		TinyV1Diff diff = new TinyV1Diff();

		MappingsDiffGenerator.run(a, b, diff);
		TinyV1DiffWriter.write(diffPath, diff);
	}

	public static void diffTinyV2Mappings(Path pathA, Path pathB, Path diffPath) throws Exception {
		TinyV2Mappings a = TinyV2Reader.read(pathA);
		TinyV2Mappings b = TinyV2Reader.read(pathB);
		TinyV2Diff diff = new TinyV2Diff();

		MappingsDiffGenerator.run(a, b, diff);
		TinyV2DiffWriter.write(diffPath, diff);
	}

	public static void applyTinyV1Diffs(Path srcPath, Path dstPath, Path... diffPaths) throws Exception {
		TinyV1Mappings src = TinyV1Reader.read(srcPath);
		TinyV1Mappings dst = src.copy();
		TinyV1Diff[] diffs = new TinyV1Diff[diffPaths.length];
		for (int i = 0; i < diffPaths.length; i++) {
			diffs[i] = TinyV1DiffReader.read(diffPaths[i]);
		}

		MappingsDiffApplier.run(src, dst, diffs);
		TinyV1Writer.write(dstPath, dst);
	}

	public static void applyTinyV2Diffs(Path srcPath, Path dstPath, Path... diffPaths) throws Exception {
		TinyV2Mappings src = TinyV2Reader.read(srcPath);
		TinyV2Mappings dst = src.copy();
		TinyV2Diff[] diffs = new TinyV2Diff[diffPaths.length];
		for (int i = 0; i < diffPaths.length; i++) {
			diffs[i] = TinyV2DiffReader.read(diffPaths[i]);
		}

		MappingsDiffApplier.run(src, dst, diffs);
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
