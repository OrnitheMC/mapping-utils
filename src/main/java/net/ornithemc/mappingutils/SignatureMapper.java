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
				}
			}
		});
	}

	private SignatureMappings run() {
		for (ClassMapping c : sigsIn.getClasses()) {
			String cname = remapper.map(c.getName());
			SignatureMode cmode = c.getMode();
			String csignature = remapSignature(c.getSignature());

			ClassMapping cout = sigsOut.addClass(cname, cmode, csignature);

			for (MemberMapping m : c.getMembers()) {
				String mname = m.getName();
				String mdesc = remapper.mapDesc(m.getDesc());
				SignatureMode mmode = m.getMode();
				String msignature = remapSignature(m.getSignature());

				cout.addMember(mname, mdesc, mmode, msignature);
			}
		}

		return this.sigsOut;
	}

	private String remapSignature(String signature) {
		SignatureReader reader = new SignatureReader(signature);
		SignatureWriter writer = new SignatureWriter();
		reader.accept(new SignatureRemapper(writer, remapper));
		return writer.toString();
	}
}
