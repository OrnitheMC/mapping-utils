package net.ornithemc.mappingutils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.objectweb.asm.Type;

import io.github.gaming32.signaturechanger.tree.SigsFile;
import io.github.gaming32.signaturechanger.visitor.SigsFileWriter;
import io.github.gaming32.signaturechanger.visitor.SigsReader;

import net.ornithemc.exceptor.io.ExceptionsFile;
import net.ornithemc.exceptor.io.ExceptorIo;
import net.ornithemc.mappingutils.io.Format;
import net.ornithemc.mappingutils.io.MappingNamespace;
import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.graph.MappingHistory;
import net.ornithemc.mappingutils.io.diff.graph.Version;
import net.ornithemc.mappingutils.io.diff.graph.VersionGraph;
import net.ornithemc.mappingutils.io.matcher.MatchSide;
import net.ornithemc.mappingutils.io.matcher.MatchesReader;
import net.ornithemc.mappingutils.io.matcher.MatchesWriter;

import net.ornithemc.nester.nest.NesterIo;
import net.ornithemc.nester.nest.Nests;

public class MappingUtils {

	public static void invertMatches(Path src, Path dst) throws IOException {
		FileUtils.requireReadable(src);
		FileUtils.requireWritable(dst);

		MatchesWriter.write(dst, MatchesReader.read(src), MatchSide.B);
	}

	public static void diffMappings(Format format, Path pathA, Path pathB, Path diffPath) throws IOException {
		FileUtils.requireReadable(pathA);
		FileUtils.requireReadable(pathB);
		FileUtils.requireWritable(diffPath);

		Mappings a = format.readMappings(pathA);
		Mappings b = format.readMappings(pathB);

		format.writeDiff(diffPath, diffMappings(a, b));
	}

	public static MappingsDiff diffMappings(Mappings a, Mappings b) {
		return DiffGenerator.run(a, b);
	}

	public static void applyDiffs(Format format, Path srcPath, Path dstPath, Path... diffPaths) throws IOException {
		applyDiffs(format, srcPath, dstPath, Arrays.asList(diffPaths));
	}

	public static void applyDiffs(Format format, Path srcPath, Path dstPath, List<Path> diffPaths) throws IOException {
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

	public static void applyDiffs(Mappings mappings, MappingsDiff... diffs) {
		DiffApplier.run(mappings, diffs);
	}

	public static void applyDiffs(Mappings mappings, List<MappingsDiff> diffs) {
		DiffApplier.run(mappings, diffs);
	}

	public static void separateMappings(Format format, Path dir, Path dstPath, String version) throws IOException {
		FileUtils.requireReadable(dir);
		FileUtils.requireWritable(dstPath);

		VersionGraph graph = VersionGraph.of(format, dir);

		format.writeMappings(dstPath, separateMappings(graph, version));
	}

	public static Mappings separateMappings(VersionGraph graph, String version) throws IOException {
		Version root = graph.root();

		Mappings mappings = root.getMappings().copy();
		Collection<MappingsDiff> diffs = graph.getDiffsFromRoot(version);

		DiffApplier.run(mappings, diffs);

		return mappings;
	}

	public static void insertMappings(Format format, PropagationOptions options, Path dirPath, Path changesPath, String version) throws IOException {
		FileUtils.requireReadable(dirPath);
		FileUtils.requireReadable(changesPath);

		VersionGraph graph = VersionGraph.of(format, dirPath);
		MappingsDiff changes = format.readDiff(changesPath);

		insertMappings(options, graph, changes, version);
	}

	public static void insertMappings(PropagationOptions options, VersionGraph graph, MappingsDiff changes, String version) throws IOException {
		Propagator.run(options, graph, changes, version);
	}

	public static void generateDummyMappings(Format format, MappingNamespace srcNamespace, MappingNamespace dstNamespace, String classNamePattern, Path jarPath, Path mappingsPath) throws IOException {
		format.writeMappings(mappingsPath, generateDummyMappings(srcNamespace, dstNamespace, classNamePattern, jarPath));
	}

	public static Mappings generateDummyMappings(MappingNamespace srcNamespace, MappingNamespace dstNamespace, String classNamePattern, Path jarPath) throws IOException {
		return DummyGenerator.run(srcNamespace, dstNamespace, classNamePattern, jarPath);
	}

	public static void applyNests(Format format, Path srcPath, Path dstPath, Path nestsPath) throws IOException {
		runNester(format, srcPath, dstPath, nestsPath, true);
	}

	public static void undoNests(Format format, Path srcPath, Path dstPath, Path nestsPath) throws IOException {
		runNester(format, srcPath, dstPath, nestsPath, false);
	}

	private static void runNester(Format format, Path srcPath, Path dstPath, Path nestsPath, boolean apply) throws IOException {
		Mappings mappings = format.readMappings(srcPath);
		Nests nests = Nests.of(nestsPath);

		Mappings nestedMappings = runNester(mappings, nests, apply);
		format.writeMappings(dstPath, nestedMappings);
	}

	public static Mappings applyNests(Mappings mappings, Nests nests) {
		return runNester(mappings, nests, true);
	}

	public static Mappings undoNests(Mappings mappings, Nests nests)  {
		return runNester(mappings, nests, false);
	}

	private static Mappings runNester(Mappings mappings, Nests nests, boolean apply) {
		return Nester.run(mappings, nests, apply);
	}

	public static void applyNestsToExceptions(Path srcPath, Path dstPath, Path nestsPath) throws IOException {
		runExceptionsNester(srcPath, dstPath, nestsPath, true);
	}

	public static void undoNestsToExceptions(Path srcPath, Path dstPath, Path nestsPath) throws IOException {
		runExceptionsNester(srcPath, dstPath, nestsPath, false);
	}

	private static void runExceptionsNester(Path srcPath, Path dstPath, Path nestsPath, boolean apply) throws IOException {
		ExceptionsFile exceptions = ExceptorIo.read(srcPath);
		Nests nests = Nests.of(nestsPath);

		ExceptionsFile nestedExceptions = runExceptionsNester(exceptions, nests, apply);
		ExceptorIo.write(dstPath, nestedExceptions);
	}

	public static ExceptionsFile applyNestsToExceptions(ExceptionsFile exceptions, Nests nests) {
		return runExceptionsNester(exceptions, nests, true);
	}

	public static ExceptionsFile undoNestsToExceptions(ExceptionsFile exceptions, Nests nests)  {
		return runExceptionsNester(exceptions, nests, false);
	}

	private static ExceptionsFile runExceptionsNester(ExceptionsFile exceptions, Nests nests, boolean apply) {
		return ExceptionsNester.run(exceptions, nests, apply);
	}

	public static void mapExceptions(Path exceptionsInPath, Path exceptionsOutPath, Format format, Path mappingsPath) throws IOException {
		ExceptionsFile exceptionsIn = ExceptorIo.read(exceptionsInPath);
		Mappings mappings = format.readMappings(mappingsPath);

		ExceptionsFile exceptionsOut = mapExceptions(exceptionsIn, mappings);
		ExceptorIo.write(exceptionsOutPath, exceptionsOut);
	}

	public static ExceptionsFile mapExceptions(ExceptionsFile exceptions, Mappings mappings) {
		return ExceptionsMapper.run(exceptions, mappings);
	}

	public static void mergeExceptions(Path clientPath, Path serverPath, Path mergedPath) throws IOException {
		ExceptionsFile client = ExceptorIo.read(clientPath);
		ExceptionsFile server = ExceptorIo.read(serverPath);

		ExceptionsFile merged = mergeExceptions(client, server);
		ExceptorIo.write(mergedPath, merged);
	}

	public static ExceptionsFile mergeExceptions(ExceptionsFile client, ExceptionsFile server) {
		return ExceptionsMerger.run(client, server);
	}

	public static void mapNests(Path srcPath, Path dstPath, Format format, Path mappingsPath) throws IOException {
		Nests src = Nests.empty();
		NesterIo.read(src, srcPath);
		Mappings mappings = format.readMappings(mappingsPath);

		Nests dst = mapNests(src, mappings);
		NesterIo.write(dst, dstPath);
	}

	public static Nests mapNests(Nests nests, Mappings mappings) {
		return mapNests(nests, Mapper.of(mappings));
	}

	public static Nests mapNests(Nests nests, Mapper mapper) {
		return NestsMapper.run(nests, mapper);
	}

	public static void mergeNests(Path clientPath, Path serverPath, Path mergedPath) throws IOException {
		Nests client = Nests.empty();
		NesterIo.read(client, clientPath);
		Nests server = Nests.empty();
		NesterIo.read(server, serverPath);

		Nests merged = mergeNests(client, server);
		NesterIo.write(merged, mergedPath);
	}

	public static Nests mergeNests(Nests client, Nests server) {
		return NestsMerger.run(client, server);
	}

	public static void mapSignatures(Path sigsInPath, Path sigsOutPath, Format format, Path mappingsPath) throws IOException {
		SigsFile sigsIn = new SigsFile();
		try (SigsReader sr = new SigsReader(Files.newBufferedReader(sigsInPath))) {
			sr.accept(sigsIn);
		}
		Mappings mappings = format.readMappings(mappingsPath);

		SigsFile sigsOut = mapSignatures(sigsIn, mappings);
		try (BufferedWriter bw = Files.newBufferedWriter(sigsOutPath)) {
			sigsOut.accept(new SigsFileWriter(bw));
		}
	}

	public static SigsFile mapSignatures(SigsFile sigs, Mappings mappings) {
		return SignatureMapper.run(sigs, mappings);
	}

	public static void mergeSignatures(Path clientPath, Path serverPath, Path mergedPath) throws IOException {
		SigsFile client = new SigsFile();
		SigsFile server = new SigsFile();
		try (SigsReader sr = new SigsReader(Files.newBufferedReader(clientPath))) {
			sr.accept(client);
		}
		try (SigsReader sr = new SigsReader(Files.newBufferedReader(serverPath))) {
			sr.accept(server);
		}

		SigsFile merged = mergeSignatures(client, server);
		try (BufferedWriter bw = Files.newBufferedWriter(mergedPath)) {
			merged.accept(new SigsFileWriter(bw));
		}
	}

	public static SigsFile mergeSignatures(SigsFile client, SigsFile server) {
		return SignatureMerger.run(client, server);
	}

	public static Collection<MappingHistory> findMappings(Format format, Path dir, MappingTarget target, String key) throws IOException {
		FileUtils.requireReadable(dir);

		VersionGraph graph = VersionGraph.of(format, dir);

		return findMappings(graph, target, key);
	}

	public static Collection<MappingHistory> findMappings(VersionGraph graph, MappingTarget target, String key) throws IOException {
		return Finder.run(graph, target, key);
	}

	public static Collection<MappingHistory> findMappingHistories(Format format, Path dir, MappingTarget target, String key) throws IOException {
		FileUtils.requireReadable(dir);

		VersionGraph graph = VersionGraph.of(format, dir);

		return findMappingHistories(graph, target, key);
	}

	public static Collection<MappingHistory> findMappingHistories(VersionGraph graph, MappingTarget target, String key) throws IOException {
		return HistoryFinder.run(graph, target, key);
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

	public static void main(String[] args) throws Throwable {
	}
}
