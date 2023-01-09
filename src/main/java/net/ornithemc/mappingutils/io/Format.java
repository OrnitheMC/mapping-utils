package net.ornithemc.mappingutils.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.tiny.v1.TinyV1DiffReader;
import net.ornithemc.mappingutils.io.diff.tiny.v1.TinyV1DiffWriter;
import net.ornithemc.mappingutils.io.diff.tiny.v2.TinyV2DiffReader;
import net.ornithemc.mappingutils.io.diff.tiny.v2.TinyV2DiffWriter;
import net.ornithemc.mappingutils.io.enigma.dir.EnigmaDirReader;
import net.ornithemc.mappingutils.io.enigma.dir.EnigmaDirWriter;
import net.ornithemc.mappingutils.io.enigma.file.EnigmaFileReader;
import net.ornithemc.mappingutils.io.enigma.file.EnigmaFileWriter;
import net.ornithemc.mappingutils.io.tiny.v1.TinyV1Reader;
import net.ornithemc.mappingutils.io.tiny.v1.TinyV1Writer;
import net.ornithemc.mappingutils.io.tiny.v2.TinyV2Reader;
import net.ornithemc.mappingutils.io.tiny.v2.TinyV2Writer;

public enum Format {

	TINY_V1(".tiny", ".tinydiff") {

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
			TinyV1Writer.write(path, mappings);
		}

		@Override
		public void writeMappings(BufferedWriter bw, Mappings mappings) throws Exception {
			TinyV1Writer.write(bw, mappings);
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
			TinyV1DiffWriter.write(path, diff);
		}

		@Override
		public void writeDiff(BufferedWriter bw, MappingsDiff diff) throws Exception {
			TinyV1DiffWriter.write(bw, diff);
		}
	},
	TINY_V2(".tiny", ".tinydiff") {

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
			TinyV2Writer.write(path, mappings);
		}

		@Override
		public void writeMappings(BufferedWriter bw, Mappings mappings) throws Exception {
			TinyV2Writer.write(bw, mappings);
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
			TinyV2DiffWriter.write(path, diff);
		}

		@Override
		public void writeDiff(BufferedWriter bw, MappingsDiff diff) throws Exception {
			TinyV2DiffWriter.write(bw, diff);
		}
	},
	ENIGMA_FILE(".mapping", null) {

		@Override
		public Mappings readMappings(Path path) throws Exception {
			return EnigmaFileReader.read(path);
		}

		@Override
		public Mappings readMappings(BufferedReader br) throws Exception {
			return EnigmaFileReader.read(br);
		}

		@Override
		public void writeMappings(Path path, Mappings mappings) throws Exception {
			EnigmaFileWriter.write(path, mappings);
		}

		@Override
		public void writeMappings(BufferedWriter bw, Mappings mappings) throws Exception {
			EnigmaFileWriter.write(bw, mappings);
		}
	},
	ENIGMA_DIR(null, null) {

		@Override
		public Mappings readMappings(Path path) throws Exception {
			return EnigmaDirReader.read(path);
		}

		@Override
		public void writeMappings(Path path, Mappings mappings) throws Exception {
			EnigmaDirWriter.write(path, mappings);
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

	public Mappings readMappings(Path path) throws Exception {
		throw new UnsupportedOperationException();
	}

	public Mappings readMappings(BufferedReader br) throws Exception {
		throw new UnsupportedOperationException();
	}

	public void writeMappings(Path path, Mappings mappings) throws Exception {
		throw new UnsupportedOperationException();
	}

	public void writeMappings(BufferedWriter bw, Mappings mappings) throws Exception {
		throw new UnsupportedOperationException();
	}

	public MappingsDiff readDiff(Path path) throws Exception {
		throw new UnsupportedOperationException();
	}

	public MappingsDiff readDiff(BufferedReader br) throws Exception {
		throw new UnsupportedOperationException();
	}

	public void writeDiff(Path path, MappingsDiff diff) throws Exception {
		throw new UnsupportedOperationException();
	}

	public void writeDiff(BufferedWriter bw, MappingsDiff diff) throws Exception {
		throw new UnsupportedOperationException();
	}
}
