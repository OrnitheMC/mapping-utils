package net.ornithemc.mappingutils;

import java.util.HashMap;

import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SignatureRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureWriter;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.sigs.SignatureMappings;
import net.ornithemc.mappingutils.io.sigs.SignatureMappings.ClassMapping;
import net.ornithemc.mappingutils.io.sigs.SignatureMappings.MemberMapping;
import net.ornithemc.mappingutils.io.sigs.SignatureMode;

class SignatureMapper {

	static SignatureMappings run(SignatureMappings sigs, Mappings mappings) {
		return new SignatureMapper(sigs, mappings).run();
	}

	private final SignatureMappings sigsIn;
	private final SignatureMappings sigsOut;
	private final Remapper remapper;

	private SignatureMapper(SignatureMappings sigs, Mappings mappings) {
		this.sigsIn = sigs;
		this.sigsOut = new SignatureMappings();
		this.remapper = new SimpleRemapper(new HashMap<String, String>() {

			{
				for (net.ornithemc.mappingutils.io.Mappings.ClassMapping c : mappings.getClasses()) {
					put(c.src(), c.getComplete());
					for (net.ornithemc.mappingutils.io.Mappings.FieldMapping f : c.getFields()) {
						put(c.src() + "." + f.src(), f.get());
					}
					for (net.ornithemc.mappingutils.io.Mappings.MethodMapping m : c.getMethods()) {
						put(c.src() + "." + m.src() + m.getDesc(), m.get());
					}
				}
			}

			@Override
			public String get(Object key) {
				String value = super.get(key);
				return value == null ? (String)key : value;
			}
		});
	}

	private SignatureMappings run() {
		int line = 0;

		try {
			for (ClassMapping c : sigsIn.getClasses()) {
				line++;

				String cname = c.getName();
				SignatureMode cmode = c.getMode();
				String csignature = c.getSignature();
				cname = remapper.map(cname);
				csignature = remapSignature(csignature);

				ClassMapping cout = sigsOut.addClass(cname, cmode, csignature);

				for (MemberMapping m : c.getMembers()) {
					line++;

					String mname = m.getName();
					String mdesc = m.getDesc();
					SignatureMode mmode = m.getMode();
					String msignature = m.getSignature();
					mname = mdesc.charAt(0) == '(' ? remapper.mapMethodName(c.getName(), mname, mdesc) : remapper.mapFieldName(c.getName(), mname, mdesc);
					mdesc = remapper.mapDesc(mdesc);
					msignature = remapSignature(msignature);

					cout.addMember(mname, mdesc, mmode, msignature);
				}
			}
		} catch (Throwable t) {
			throw new RuntimeException("exception on line " + line, t);
		}

		return this.sigsOut;
	}

	private String remapSignature(String signature) {
		if (signature.isEmpty()) {
			return null;
		} else {
			SignatureReader reader = new SignatureReader(signature);
			SignatureWriter writer = new SignatureWriter();
			reader.accept(new SignatureRemapper(writer, remapper));
			return writer.toString();
		}
	}
}
