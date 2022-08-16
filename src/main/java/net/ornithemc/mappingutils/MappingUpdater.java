package net.ornithemc.mappingutils;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.Mappings.ParameterMapping;
import net.ornithemc.mappingutils.io.matcher.MatchSide;
import net.ornithemc.mappingutils.io.matcher.Matches;
import net.ornithemc.mappingutils.io.matcher.Matches.ClassMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.FieldMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.MethodMatch;

/**
 * To update mappings between versions, neither the mapped names nor
 * the source names they target should change. However, field and
 * method descriptors might have change, so those will need to be
 * updated.
 * 
 * @author Space Walker
 */
class MappingUpdater {

	static void run(Mappings src, Mappings dst, Mappings calamusSrc, Mappings calamusDst, Matches matches) {
		new MappingUpdater(src, dst, calamusSrc, calamusDst, matches).run();
	}

	private final Mappings src;
	private final Mappings dst;
	private final Mappings calamusSrc;
	private final Mappings calamusSrcInv;
	private final Mappings calamusDst;
	private final Matches matches;

	private MappingUpdater(Mappings src, Mappings dst, Mappings calamusSrc, Mappings calamusDst, Matches matches) {
		this.src = src;
		this.dst = dst;
		this.calamusSrc = calamusSrc;
		this.calamusSrcInv = this.calamusSrc.invert();
		this.calamusDst = calamusDst;
		this.matches = matches;
	}

	public void run() {
		for (ClassMapping c : src.getTopLevelClasses()) {
			updateClass(c);
		}
	}

	private void updateClass(ClassMapping c) {
		ClassMapping cCalamusSrcInv = calamusSrcInv.getClass(c.src());
		ClassMatch cMatch = matches.getClass(cCalamusSrcInv.get());

		// check if class exists in dst jar
		if (cMatch == null) {
			return;
		}

		ClassMapping cCalamusDst = calamusDst.getClass(cMatch.name(MatchSide.DST));
		ClassMapping dc = dst.addClass(cCalamusDst.get(), c.get());

		dc.setSimple(c.getSimple());
		dc.setJavadocs(c.getJavadocs());

		for (FieldMapping f : c.getFields()) {
			FieldMapping fCalamusSrcInv = cCalamusSrcInv.getField(f.src(), f.getDesc());
			FieldMatch fMatch = cMatch.getField(fCalamusSrcInv.get(), fCalamusSrcInv.invert().getDesc());

			if (fMatch != null) {
				FieldMapping fCalamusDst = cCalamusDst.getField(fMatch.name(MatchSide.DST), fMatch.desc(MatchSide.DST));
				FieldMapping df = dc.addField(fCalamusDst.get(), f.get(), fCalamusDst.invert().getDesc());

				df.setJavadocs(f.getJavadocs());
			}
		}
		for (MethodMapping m : c.getMethods()) {
			MethodMapping mCalamusSrcInv = cCalamusSrcInv.getMethod(m.src(), m.getDesc());
			MethodMatch mMatch = cMatch.getMethod(mCalamusSrcInv.get(), mCalamusSrcInv.invert().getDesc());

			if (mMatch != null) {
				MethodMapping mCalamusDst = cCalamusDst.getMethod(mMatch.name(MatchSide.DST), mMatch.desc(MatchSide.DST));

				// method matches are a little finnicky
				// sometimes there is a match even if the method does not exist in dst
				if (mCalamusDst != null) {
					MethodMapping dm = dc.addMethod(mCalamusDst.get(), m.get(), mCalamusDst.invert().getDesc());

					dm.setJavadocs(m.getJavadocs());

					for (ParameterMapping p : m.getParameters()) {
						ParameterMapping dp = dm.addParameter(p.src(), p.get(), p.getIndex());

						// method descriptors can be different
						// check if parameter exists in dst
						if (dp != null) {
							dp.setJavadocs(p.getJavadocs());
						}
					}
				}
			}
		}
		for (ClassMapping cc : c.getClasses()) {
			updateClass(cc);
		}
	}
}
