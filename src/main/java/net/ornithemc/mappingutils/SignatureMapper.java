package net.ornithemc.mappingutils;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SignatureRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureWriter;

import io.github.gaming32.signaturechanger.SignatureMode;
import io.github.gaming32.signaturechanger.tree.MemberReference;
import io.github.gaming32.signaturechanger.tree.SignatureInfo;
import io.github.gaming32.signaturechanger.tree.SigsClass;
import io.github.gaming32.signaturechanger.tree.SigsFile;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;

class SignatureMapper {

	static SigsFile run(SigsFile sigs, Mappings mappings) {
		return new SignatureMapper(sigs, mappings).run();
	}

	static SigsFile run(SigsFile sigs, Remapper remapper) {
		return new SignatureMapper(sigs, remapper).run();
	}

	private final SigsFile sigsIn;
	private final SigsFile sigsOut;
	private final Remapper remapper;

	private SignatureMapper(SigsFile sigs, Mappings mappings) {
		this(sigs, new SimpleRemapper(new HashMap<String, String>() {

			{
				for (ClassMapping c : mappings.getClasses()) {
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
		}));
	}

	private SignatureMapper(SigsFile sigs, Remapper remapper) {
		this.sigsIn = sigs;
		this.sigsOut = new SigsFile();
		this.remapper = remapper;
	}

	private SigsFile run() {
		int line = 0;

		try {
			Map<String, SigsClass> sigsOutSorted = new TreeMap<>();

			for (Map.Entry<String, SigsClass> ce : sigsIn.classes.entrySet()) {
				line++;

				String cname = ce.getKey();
				SigsClass c = ce.getValue();
				SignatureMode cmode = c.signatureInfo.mode();
				String csignature = c.signatureInfo.signature();
				cname = remapper.map(cname);
				csignature = remapSignature(csignature);
				
				SigsClass cout = new SigsClass();
				cout.signatureInfo = new SignatureInfo(cmode, csignature);
				sigsOutSorted.put(cname, cout);

				for (Map.Entry<MemberReference, SignatureInfo> me : c.members.entrySet()) {
					line++;

					MemberReference m = me.getKey();
					String mname = m.name();
					String mdesc = m.desc().getDescriptor();
					SignatureMode mmode = me.getValue().mode();
					String msignature = me.getValue().signature();
					mname = mdesc.charAt(0) == '(' ? remapper.mapMethodName(ce.getKey(), mname, mdesc) : remapper.mapFieldName(ce.getKey(), mname, mdesc);
					mdesc = remapper.mapDesc(mdesc);
					msignature = remapSignature(msignature);

					cout.visitMember(mname, mdesc, mmode, msignature);
				}
			}

			sigsOut.classes.putAll(sigsOutSorted);
		} catch (Throwable t) {
			throw new RuntimeException("exception on line " + line, t);
		}

		return this.sigsOut;
	}

	private String remapSignature(String signature) {
		if (signature == null || signature.isEmpty()) {
			return null;
		} else {
			SignatureReader reader = new SignatureReader(signature);
			SignatureWriter writer = new SignatureWriter();
			reader.accept(new SignatureRemapper(writer, remapper));
			return writer.toString();
		}
	}
}
