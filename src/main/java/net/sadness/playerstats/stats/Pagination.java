package net.sadness.playerstats.stats;

public record Pagination(int page, int totalPages, int pageSize, int offset) {

    public static Pagination of(long totalItems, int pageSize, int requestedPage) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("totalItems must not be negative");
        }
        long pages = Math.max(1, (totalItems + pageSize - 1) / pageSize);
        int totalPages = (int) Math.min(pages, Integer.MAX_VALUE);
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        long offset = (long) (page - 1) * pageSize;
        return new Pagination(page, totalPages, pageSize, (int) Math.min(offset, Integer.MAX_VALUE));
    }
}
