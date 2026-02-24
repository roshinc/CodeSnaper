package gov.nystax.nimbus.tools.get2git.remote.gitlab.api.domain;

import com.google.gson.annotations.SerializedName;

public class RepoFile {
	@SerializedName(value = "file_name")
	private String fileName;
	@SerializedName(value = "file_path")
	private String filePath;
	private int size;
	private String content;

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("RepoFile [fileName=");
		builder.append(fileName);
		builder.append(", filePath=");
		builder.append(filePath);
		builder.append(", size=");
		builder.append(size);
		builder.append(", content=");
		builder.append(content);
		builder.append("]");
		return builder.toString();
	}

}
