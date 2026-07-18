package org.juns.marketboardbackend.symbol;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "symbols")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Symbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String ticker;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String exchange;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int priority;

    /**
     * True for S&amp;P 500 constituents seeded by the collector's daily batch job — independent of
     * {@code active} (which controls the real-time Finnhub WS subscription set). A symbol can be
     * in the S&amp;P 500 universe without ever being WS-subscribed, and vice versa.
     */
    @Column(name = "in_sp500_universe", nullable = false)
    private boolean inSp500Universe;

    @Builder
    public Symbol(String ticker, String name, String exchange, int priority) {
        this.ticker = ticker;
        this.name = name;
        this.exchange = exchange;
        this.active = true;
        this.priority = priority;
    }

    public void updateDetails(String name, String exchange, int priority) {
        this.name = name;
        this.exchange = exchange;
        this.priority = priority;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
