package net.ornithemc.mappingutils.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.tiny.v1.TinyV1Diff;
import net.ornithemc.mappingutils.io.diff.tiny.v1.TinyV1DiffReader;
import net.ornithemc.mappingutils.io.diff.tiny.v1.TinyV1DiffWriter;
import net.ornithemc.mappingutils.io.diff.tiny.v2.TinyV2Diff;
import net.ornithemc.mappingutils.io.diff.tiny.v2.TinyV2DiffReader;
import net.ornithemc.mappingutils.io.diff.tiny.v2.TinyV2DiffWriter;
import net.ornithemc.mappingutils.io.tiny.v1.TinyV1Mappings;
import net.ornithemc.mappingutils.io.tiny.v1.TinyV1Reader;
import net.ornithemc.mappingutils.io.tiny.v1.TinyV1Writer;
import net.ornithemc.mappingutils.io.tiny.v2.TinyV2Mappings;
import net.ornithemc.mappingutils.io.tiny.v2.TinyV2Reader;
import net.ornithemc.mappingutils.io.tiny.v2.TinyV2Writer;

public enum Format {

	TINY_V1(".tiny", ".tinydiff") {

		@Override
		public Mappings newMappings() {
			return new TinyV1Mappings();
		}

		@Override
		public MappingsDiff newDiff() {
			return new TinyV1Diff();
		}

		@Override
		public Mappings readMappings(Path path) throws Exception {
			return TinyV1Reader.read(path);
		}

		@Override
		public Mappings readMappings(BufferedReader br) throws Exception {
			return TinyV1Reader.read(br);
		}

		@Override
		public void writeMappings(Path path, Mappings mappings) throws Exception {
			TinyV1Writer.write(path, (TinyV1Mappings)mappings);
		}

		@Override
		public void writeMappings(BufferedWriter bw, Mappings mappings) throws Exception {
			TinyV1Writer.write(bw, (TinyV1Mappings)mappings);
		}

		@Override
		public MappingsDiff readDiff(Path path) throws Exception {
			return TinyV1DiffReader.read(path);
		}

		@Override
		public MappingsDiff readDiff(BufferedReader br) throws Exception {
			return TinyV1DiffReader.read(br);
		}

		@Override
		public void writeDiff(Path path, MappingsDiff diff) throws Exception {
			TinyV1DiffWriter.write(path, (TinyV1Diff)diff);
		}

		@Override
		public void writeDiff(BufferedWriter bw, MappingsDiff diff) throws Exception {
			TinyV1DiffWriter.write(bw, (TinyV1Diff)diff);
		}
	},
	TINY_V2(".tiny", ".tinydiff") {

		@Override
		public Mappings newMappings() {
			return new TinyV2Mappings();
		}

		@Override
		public MappingsDiff newDiff() {
			return new TinyV2Diff();
		}

		@Override
		public Mappings readMappings(Path path) throws Exception {
			return TinyV2Reader.read(path);
		}

		@Override
		public Mappings readMappings(BufferedReader br) throws Exception {
			return TinyV2Reader.read(br);
		}

		@Override
		public void writeMappings(Path path, Mappings mappings) throws Exception {
			TinyV2Writer.write(path, (TinyV2Mappings)mappings);
		}

		@Override
		public void writeMappings(BufferedWriter bw, Mappings mappings) throws Exception {
			TinyV2Writer.write(bw, (TinyV2Mappings)mappings);
		}

		@Override
		public MappingsDiff readDiff(Path path) throws Exception {
			return TinyV2DiffReader.read(path);
		}

		@Override
		public MappingsDiff readDiff(BufferedReader br) throws Exception {
			return TinyV2DiffReader.read(br);
		}

		@Override
		public void writeDiff(Path path, MappingsDiff diff) throws Exception {
			TinyV2DiffWriter.write(path, (TinyV2Diff)diff);
		}

		@Override
		public void writeDiff(BufferedWriter bw, MappingsDiff diff) throws Exception {
			TinyV2DiffWriter.write(bw, (TinyV2Diff)diff);
		}
	};

	private final String mappingsExtension;
	private final String diffExtension;

	private Format(String mappingsExtension, String diffExtension) {
		this.mappingsExtension = mappingsExtension;
		this.diffExtension = diffExtension;
	}

	public String mappingsExtension() {
		return mappingsExtension;
	}

	public String diffExtension() {
		return diffExtension;
	}

	public abstract Mappings newMappings();

	public abstract MappingsDiff newDiff();

	public abstract Mappings readMappings(Path path) throws Exception;

	public abstract Mappings readMappings(BufferedReader br) throws Exception;

	public abstract void writeMappings(Path path, Mappings mappings) throws Exception;

	public abstract void writeMappings(BufferedWriter bw, Mappings mappings) throws Exception;

	public abstract MappingsDiff readDiff(Path path) throws Exception;

	public abstract MappingsDiff readDiff(BufferedReader br) throws Exception;

	public abstract void writeDiff(Path path, MappingsDiff diff) throws Exception;

	public abstract void writeDiff(BufferedWriter bw, MappingsDiff diff) throws Exception;

}
