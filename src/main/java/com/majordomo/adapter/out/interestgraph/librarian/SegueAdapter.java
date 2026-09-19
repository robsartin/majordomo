package com.majordomo.adapter.out.interestgraph.librarian;

import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.port.out.librarian.InterestGraphPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pushes a book's authors into Segue through its existing MCP tools.
 *
 * <p>Segue needed no new endpoint for this (ADR-0023, amended 2026-09-19): it
 * already exposes {@code add_entity} — idempotent by its own declared contract,
 * which is what makes a re-sync safe — and {@code note_affinity} for the 1-5
 * taste layer.
 *
 * <p>Order matters and is not interchangeable: {@code note_affinity} requires
 * the entity to already be in the graph, so {@code add_entity} goes first.
 */
@Component
public class SegueAdapter implements InterestGraphPort {

    private static final Logger LOG = LoggerFactory.getLogger(SegueAdapter.class);

    private final SegueMcpClient segue;

    /**
     * Constructs the adapter.
     *
     * @param segue the MCP client
     */
    public SegueAdapter(SegueMcpClient segue) {
        this.segue = segue;
    }

    @Override
    public void syncBook(Book book, Map<String, String> authorQids) {
        if (authorQids == null || authorQids.isEmpty()) {
            LOG.debug("Not syncing '{}': no author QID resolved", book.getTitle());
            return;
        }
        for (Map.Entry<String, String> author : authorQids.entrySet()) {
            String qid = author.getValue();
            segue.callTool("add_entity", Map.of("qid", qid));

            if (book.getRating() == null) {
                // note_affinity requires a rating and majordomo has none to
                // give. The author still reaches the graph as a node; what is
                // withheld is a taste claim nobody made. Inventing a default
                // here would put a fabricated opinion into a graph whose whole
                // point is recording real ones.
                LOG.debug("Added {} without an affinity: '{}' is unrated", qid, book.getTitle());
                continue;
            }
            var arguments = new LinkedHashMap<String, Object>();
            arguments.put("qid", qid);
            arguments.put("rating", book.getRating());
            arguments.put("note", noteFor(book, author.getKey()));
            segue.callTool("note_affinity", arguments);
        }
    }

    private String noteFor(Book book, String authorName) {
        return "From the book \"" + book.getTitle() + "\" by " + authorName
                + ", catalogued in majordomo.";
    }
}
