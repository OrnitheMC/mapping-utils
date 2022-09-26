package net.ornithemc.mappingutils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.objectweb.asm.Type;

import net.ornithemc.mappingutils.io.Format;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.tree.MappingsDiffTree;
import net.ornithemc.mappingutils.io.diff.tree.Version;
import net.ornithemc.mappingutils.io.matcher.Matches;
import net.ornithemc.mappingutils.io.matcher.MatchesReader;

public class MappingUtils {

	public static void updateMappings(Format namedFormat, Format intermediateFormat, Path namedSrcPath, Path namedDstPath, Path intermediateSrcPath, Path intermediateDstPath, Path matchesPath) throws Exception {
		FileUtils.requireReadable(namedSrcPath);
		FileUtils.requireWritable(namedDstPath);
		FileUtils.requireReadable(intermediateSrcPath);
		FileUtils.requireReadable(intermediateDstPath);
		FileUtils.requireReadable(matchesPath);

		Mappings namedSrc = namedFormat.readMappings(namedSrcPath);
		Mappings namedDst = namedFormat.newMappings();
		Mappings intermediateSrc = intermediateFormat.readMappings(intermediateSrcPath);
		Mappings intermediateDst = intermediateFormat.readMappings(intermediateDstPath);
		Matches matches = MatchesReader.read(matchesPath);

		namedDst.setSrcNamespace(namedSrc.getSrcNamespace());
		namedDst.setDstNamespace(namedSrc.getDstNamespace());

		MappingsUpdater.run(namedSrc, namedDst, intermediateSrc, intermediateDst, matches);
		namedFormat.writeMappings(namedDstPath, namedDst);
	}

	public static void diffMappings(Format format, Path pathA, Path pathB, Path diffPath) throws Exception {
		FileUtils.requireReadable(pathA);
		FileUtils.requireReadable(pathB);
		FileUtils.requireWritable(diffPath);

		Mappings a = format.readMappings(pathA);
		Mappings b = format.readMappings(pathB);
		MappingsDiff diff = format.newDiff();

		MappingsDiffGenerator.run(a, b, diff);
		format.writeDiff(diffPath, diff);
	}

	public static void applyDiffs(Format format, Path srcPath, Path dstPath, Path... diffPaths) throws Exception {
		applyDiffs(format, srcPath, dstPath, Arrays.asList(diffPaths));
	}

	public static void applyDiffs(Format format, Path srcPath, Path dstPath, List<Path> diffPaths) throws Exception {
		FileUtils.requireReadable(srcPath);
		FileUtils.requireWritable(dstPath);
		FileUtils.requireReadable(diffPaths);

		Mappings mappings = format.readMappings(srcPath);
		MappingsDiff[] diffs = new MappingsDiff[diffPaths.size()];
		for (int i = 0; i < diffPaths.size(); i++) {
			diffs[i] = format.readDiff(diffPaths.get(i));
		}

		MappingsDiffApplier.run(mappings, diffs);
		format.writeMappings(dstPath, mappings);
	}

	public static void separateMappings(Format format, Path dir, Path dstPath, String version) throws Exception {
		FileUtils.requireReadable(dir);
		FileUtils.requireWritable(dstPath);

		MappingsDiffTree tree = MappingsDiffTree.of(format, dir);
		Version root = tree.root();

		Mappings mappings = root.getMappings();
		List<MappingsDiff> diffs = tree.getDiffsFromRoot(version);

		MappingsDiffApplier.run(mappings, diffs);
		format.writeMappings(dstPath, mappings);
	}

	public static void insertMappings(Format format, PropagationDirection dir, Path dirPath, Path changesPath, String version) throws Exception {
		FileUtils.requireReadable(dirPath);
		FileUtils.requireReadable(changesPath);

		MappingsDiffTree tree = MappingsDiffTree.of(format, dirPath);
		MappingsDiff changes = format.readDiff(changesPath);

		MappingsDiffPropagator.run(dir, tree, changes, version);
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
