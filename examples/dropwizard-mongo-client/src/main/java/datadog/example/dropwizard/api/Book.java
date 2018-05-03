package datadog.example.dropwizard.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.Document;

public class Book {

  private final String title;
  private final int numberPages;
  private final String isbnCode;

  public Book(final String isbnCode, final String title, final int numberPages) {
    this.title = title;
    this.numberPages = numberPages;
    this.isbnCode = isbnCode;
  }

  public Book(final Document d) {
    this(d.getString("isbn"), d.getString("title"), d.getInteger("page").intValue());
  }

  @JsonProperty("ISBN")
  public String getIsbnCode() {
    return isbnCode;
  }

  public String getTitle() {
    return title;
  }

  public int getNumberPages() {
    return numberPages;
  }

  public Document toDocument() {
    return new Document("isbn", isbnCode).append("title", title).append("page", numberPages);
  }
}
