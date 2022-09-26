package net.ornithemc.mappingutils.io.diff;

import net.ornithemc.mappingutils.io.diff.MappingsDiff.ClassDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.FieldDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.MethodDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ParameterDiff;

public interface MappingsDiffValidator {

	public static final MappingsDiffValidator ALWAYS = new MappingsDiffValidator() {

		@Override
		public boolean validate(ClassDiff c) {
			return true;
		}

		@Override
		public boolean validate(FieldDiff f) {
			return true;
		}

		@Override
		public boolean validate(MethodDiff m) {
			return true;
		}

		@Override
		public boolean validate(ParameterDiff p) {
			return true;
		}
	};
	public static final MappingsDiffValidator NEVER = new MappingsDiffValidator() {

		@Override
		public boolean validate(ClassDiff c) {
			return false;
		}

		@Override
		public boolean validate(FieldDiff f) {
			return false;
		}

		@Override
		public boolean validate(MethodDiff m) {
			return false;
		}

		@Override
		public boolean validate(ParameterDiff p) {
			return false;
		}
	};

	default boolean validate(Diff<?> mapping) {
		switch (mapping.target()) {
		case CLASS:
			return validate((ClassDiff)mapping);
		case FIELD:
			return validate((FieldDiff)mapping);
		case METHOD:
			return validate((MethodDiff)mapping);
		case PARAMETER:
			return validate((ParameterDiff)mapping);
		default:
			return false;
		}
	}

	boolean validate(ClassDiff c);

	boolean validate(FieldDiff f);

	boolean validate(MethodDiff m);

	boolean validate(ParameterDiff p);

}
