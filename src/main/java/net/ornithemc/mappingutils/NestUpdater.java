package net.ornithemc.mappingutils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.matcher.MatchSide;
import net.ornithemc.mappingutils.io.matcher.Matches;
import net.ornithemc.mappingutils.io.matcher.Matches.ClassMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.MethodMatch;

class NestUpdater {

	static void run(Path src, Path dst, Matches matches, MatchSide srcSide) throws IOException {
		new NestUpdater(src, dst, matches, srcSide).run();
	}

	private final Path src;
	private final Path dst;
	private final Matches matches;
	private final MatchSide srcSide;

	private NestUpdater(Path src, Path dst, Matches matches, MatchSide srcSide) {
		this.src = src;
		this.dst = dst;
		this.matches = matches;
		this.srcSide = srcSide;
	}

	private void run() throws IOException {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(dst.toFile()))) {
			try (BufferedReader br = new BufferedReader(new FileReader(src.toFile()))) {
				String line;

				while ((line = br.readLine()) != null) {
					String[] args = line.split("\\s");

					if (args.length != 6) {
						System.err.println("Incorrect number of arguments for mapping \'" + line + "\' - expected 6, got " + args.length + "...");
						continue;
					}

					String className = args[0];
					String enclClassName = args[1];
					String enclMethodName = args[2];
					String enclMethodDesc = args[3];
					String innerName = args[4];
					String accessString = args[5];

					if (className == null || className.isEmpty()) {
						System.err.println("Invalid mapping \'" + line + "\': missing class name argument!");
						continue;
					}
					if (enclClassName == null || enclClassName.isEmpty()) {
						System.err.println("Invalid mapping \'" + line + "\': missing enclosing class name argument!");
						continue;
					}
					if (innerName == null || innerName.isEmpty()) {
						System.err.println("Invalid mapping \'" + line + "\': missing inner class name argument!");
						continue;
					}

					boolean emptyName = (enclMethodName == null) || enclMethodName.isEmpty();
					boolean emptyDesc = (enclMethodDesc == null) || enclMethodDesc.isEmpty();

					if (emptyName || emptyDesc) {
						enclMethodName = null;
						enclMethodDesc = null;
					}

					int indexLength = 0;
					int anonIndex = -1;
					int localIndex = -1;

					try {
						int i = 0;

						for (; i < innerName.length(); i++) {
							if (!Character.isDigit(innerName.charAt(i)))
								break;
						}

						if (i > 0) {
							indexLength = i;

							if (i < innerName.length()) {
								localIndex = Integer.parseInt(innerName.substring(0, i));
							} else {
								anonIndex = Integer.parseInt(innerName.substring(0, i));
							}
						}
					} catch (NumberFormatException e) {

					}

					Integer access = null;

					try {
						access = Integer.parseInt(accessString);
					} catch (NumberFormatException e) {

					}

					if (access == null || access < 0) {
						System.err.println("Invalid mapping \'" + line + "\': invalid access flags!");
						continue;
					}

					ClassMatch classMatch = matches.getClass(className, srcSide);

					if (classMatch == null || classMatch.get(srcSide.opposite()).isEmpty()) {
						System.err.println("Skipping mapping \'" + line + "\': class has no match or does not exist!");
						continue;
					}

					ClassMatch enclClassMatch = matches.getClass(enclClassName, srcSide);

					if (enclClassMatch == null) {
						System.err.println("warning for mapping \'" + line + "\': enclosing class appears to not exist, but the class matches to " + classMatch.get(srcSide.opposite()));
					} else if (enclClassMatch.get(srcSide.opposite()).isEmpty()) {
						System.err.println("Skipping mapping \'" + line + "\': enclosing class has no match!");
						continue;
					}

					MethodMatch enclMethodMatch = null;

					if (enclMethodName != null) {
						enclMethodMatch = (enclClassMatch == null) ? null : enclClassMatch.getMethod(enclMethodName, enclMethodDesc, srcSide);

						if (enclMethodMatch == null || enclMethodMatch.get(srcSide.opposite()).isEmpty()) {
							System.err.println("Skipping mapping \'" + line + "\': enclosing method has no match or does not exist!");
							continue;
						}
					}

					className = classMatch.name(srcSide.opposite());
					if (enclClassMatch != null) {
						enclClassName = enclClassMatch.name(srcSide.opposite());
					}
					if (enclMethodMatch != null) {
						enclMethodName = enclMethodMatch.name(srcSide.opposite());
						enclMethodDesc = enclMethodMatch.desc(srcSide.opposite());
					} else {
						enclMethodName = "";
						enclMethodDesc = "";
					}
					if (anonIndex < 0 && localIndex < 0) {
						innerName = className.substring(className.lastIndexOf('/') + 1);
					} else {
						innerName = innerName.substring(0, indexLength);

						if (localIndex >= 0) {
							innerName += className.substring(className.lastIndexOf('/') + 1);
						}
					}

					bw.write(className);
					bw.write("\t");
					bw.write(enclClassName);
					bw.write("\t");
					bw.write(enclMethodName);
					bw.write("\t");
					bw.write(enclMethodDesc);
					bw.write("\t");
					bw.write(innerName);
					bw.write("\t");
					bw.write(accessString);
					bw.newLine();
				}
			}
		}
	}
}
