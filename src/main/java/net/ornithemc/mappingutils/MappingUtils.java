package net.ornithemc.mappingutils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.objectweb.asm.Type;

import net.ornithemc.mappingutils.io.Format;
import net.ornithemc.mappingutils.io.MappingNamespace;
import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.tree.MappingHistory;
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

		format.writeDiff(diffPath, diffMappings(format, a, b));
	}

	public static MappingsDiff diffMappings(Format format, Mappings a, Mappings b) throws Exception {
		return MappingsDiffGenerator.run(format, a, b);
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

		applyDiffs(mappings, diffs);
		format.writeMappings(dstPath, mappings);
	}

	public static void applyDiffs(Mappings mappings, MappingsDiff... diffs) throws Exception {
		MappingsDiffApplier.run(mappings, diffs);
	}

	public static void applyDiffs(Mappings mappings, List<MappingsDiff> diffs) throws Exception {
		MappingsDiffApplier.run(mappings, diffs);
	}

	public static void separateMappings(Format format, Path dir, Path dstPath, String version) throws Exception {
		FileUtils.requireReadable(dir);
		FileUtils.requireWritable(dstPath);

		MappingsDiffTree tree = MappingsDiffTree.of(format, dir);

		format.writeMappings(dstPath, separateMappings(tree, version));
	}

	public static Mappings separateMappings(MappingsDiffTree tree, String version) throws Exception {
		Version root = tree.root();

		Mappings mappings = root.getMappings().copy();
		List<MappingsDiff> diffs = tree.getDiffsFromRoot(version);

		MappingsDiffApplier.run(mappings, diffs);

		return mappings;
	}

	public static void insertMappings(Format format, PropagationDirection dir, Path dirPath, Path changesPath, String version) throws Exception {
		FileUtils.requireReadable(dirPath);
		FileUtils.requireReadable(changesPath);

		MappingsDiffTree tree = MappingsDiffTree.of(format, dirPath);
		MappingsDiff changes = format.readDiff(changesPath);

		insertMappings(dir, tree, changes, version);
	}

	public static void insertMappings(PropagationDirection dir, MappingsDiffTree tree, MappingsDiff diff, String version) throws Exception {
		MappingsDiffPropagator.run(dir, tree, diff, version);
	}

	public static void generateDummyMappings(Format format, MappingNamespace srcNamespace, MappingNamespace dstNamespace, Path jarPath, Path mappingsPath) throws Exception {
		format.writeMappings(mappingsPath, generateDummyMappings(format, srcNamespace, dstNamespace, jarPath));
	}

	public static Mappings generateDummyMappings(Format format, MappingNamespace srcNamespace, MappingNamespace dstNamespace, Path jarPath) throws Exception {
		return DummyMappingsGenerator.run(format, srcNamespace, dstNamespace, jarPath);
	}

	public static Collection<MappingHistory> findMappings(Format format, Path dir, MappingTarget target, String key) throws Exception {
		FileUtils.requireReadable(dir);

		MappingsDiffTree tree = MappingsDiffTree.of(format, dir);

		return findMappings(tree, target, key);
	}

	public static Collection<MappingHistory> findMappings(MappingsDiffTree tree, MappingTarget target, String key) throws Exception {
		return MappingFinder.run(tree, target, key);
	}

	public static Collection<MappingHistory> findMappingHistories(Format format, Path dir, MappingTarget target, String key) throws Exception {
		FileUtils.requireReadable(dir);

		MappingsDiffTree tree = MappingsDiffTree.of(format, dir);

		return findMappingHistories(tree, target, key);
	}

	public static Collection<MappingHistory> findMappingHistories(MappingsDiffTree tree, MappingTarget target, String key) throws Exception {
		return MappingHistoryFinder.run(tree, target, key);
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
				className = mapping.getComplete();
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
