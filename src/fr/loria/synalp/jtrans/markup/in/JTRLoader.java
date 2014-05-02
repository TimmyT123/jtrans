package fr.loria.synalp.jtrans.markup.in;

import fr.loria.synalp.jtrans.facade.Project;
import fr.loria.synalp.jtrans.utils.FileUtils;

import static fr.loria.synalp.jtrans.markup.jtr.JTR.*;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

/**
 * Parser for JTrans's JSON format.
 */
public class JTRLoader implements MarkupLoader {

	@Override
	public Project parse(File file) throws ParsingException, IOException {
		Reader r = FileUtils.getUTF8Reader(file);
		Project project = newGson().fromJson(r, Project.class);
		r.close();
		return project;
	}


	@Override
	public String getFormat() {
		return "JTrans/JSON (\"JTR\")";
	}


	@Override
	public String getExt() {
		return ".jtr";
	}

}
