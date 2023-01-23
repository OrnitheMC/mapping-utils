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
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.tree.MappingHistory;
import net.ornithemc.mappingutils.io.diff.tree.MappingsDiffTree;
import net.ornithemc.mappingutils.io.diff.tree.Version;
import net.ornithemc.mappingutils.io.matcher.MatchSide;
import net.ornithemc.mappingutils.io.matcher.MatchesReader;
import net.ornithemc.mappingutils.io.matcher.MatchesWriter;

public class MappingUtils {

	public static void invertMatches(Path src, Path dst) throws Exception {
		FileUtils.requireReadable(src);
		FileUtils.requireWritable(dst);

		MatchesWriter.write(dst, MatchesReader.read(src), MatchSide.B);
	}

	public static void diffMappings(Format format, Path pathA, Path pathB, Path diffPath) throws Exception {
		FileUtils.requireReadable(pathA);
		FileUtils.requireReadable(pathB);
		FileUtils.requireWritable(diffPath);

		Mappings a = format.readMappings(pathA);
		Mappings b = format.readMappings(pathB);

		format.writeDiff(diffPath, diffMappings(a, b));
	}

	public static MappingsDiff diffMappings(Mappings a, Mappings b) throws Exception {
		return DiffGenerator.run(a, b);
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
		DiffApplier.run(mappings, diffs);
	}

	public static void applyDiffs(Mappings mappings, List<MappingsDiff> diffs) throws Exception {
		DiffApplier.run(mappings, diffs);
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

		DiffApplier.run(mappings, diffs);

		return mappings;
	}

	public static void insertMappings(Format format, PropagationOptions options, Path dirPath, Path changesPath, String version) throws Exception {
		FileUtils.requireReadable(dirPath);
		FileUtils.requireReadable(changesPath);

		MappingsDiffTree tree = MappingsDiffTree.of(format, dirPath);
		MappingsDiff changes = format.readDiff(changesPath);

		insertMappings(options, tree, changes, version);
	}

	public static void insertMappings(PropagationOptions options, MappingsDiffTree tree, MappingsDiff diff, String version) throws Exception {
		ChangePropagator.run(options, tree, diff, version);
	}

	public static void generateDummyMappings(Format format, MappingNamespace srcNamespace, MappingNamespace dstNamespace, String classNamePattern, Path jarPath, Path mappingsPath) throws Exception {
		format.writeMappings(mappingsPath, generateDummyMappings(srcNamespace, dstNamespace, classNamePattern, jarPath));
	}

	public static Mappings generateDummyMappings(MappingNamespace srcNamespace, MappingNamespace dstNamespace, String classNamePattern, Path jarPath) throws Exception {
		return DummyGenerator.run(srcNamespace, dstNamespace, classNamePattern, jarPath);
	}

	public static Collection<MappingHistory> findMappings(Format format, Path dir, MappingTarget target, String key) throws Exception {
		FileUtils.requireReadable(dir);

		MappingsDiffTree tree = MappingsDiffTree.of(format, dir);

		return findMappings(tree, target, key);
	}

	public static Collection<MappingHistory> findMappings(MappingsDiffTree tree, MappingTarget target, String key) throws Exception {
		return Finder.run(tree, target, key);
	}

	public static Collection<MappingHistory> findMappingHistories(Format format, Path dir, MappingTarget target, String key) throws Exception {
		FileUtils.requireReadable(dir);

		MappingsDiffTree tree = MappingsDiffTree.of(format, dir);

		return findMappingHistories(tree, target, key);
	}

	public static Collection<MappingHistory> findMappingHistories(MappingsDiffTree tree, MappingTarget target, String key) throws Exception {
		return HistoryFinder.run(tree, target, key);
	}

	public static String translateFieldDescriptor(String desc, Mapper mapper) {
		Type type = Type.getType(desc);
		type = translateType(type, mapper);

		return type.getDescriptor();
	}

	public static String translateMethodDescriptor(String desc, Mapper mapper) {
		Type type = Type.getMethodType(desc);

		Type[] argTypes = type.getArgumentTypes();
		Type returnType = type.getReturnType();

		for (int i = 0; i < argTypes.length; i++) {
			argTypes[i] = translateType(argTypes[i], mapper);
		}
		returnType = translateType(returnType, mapper);

		type = Type.getMethodType(returnType, argTypes);

		return type.getDescriptor();
	}

	public static Type translateType(Type type, Mapper mapper) {
		switch (type.getSort()) {
		case Type.OBJECT:
			String className = type.getInternalName();
			className = mapper.mapClass(className);
			type = Type.getObjectType(className);

			break;
		case Type.ARRAY:
			Type elementType = type.getElementType();
			elementType = translateType(elementType, mapper);

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
