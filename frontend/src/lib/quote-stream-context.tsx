'use client';

import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useToast } from '@astryxdesign/core/Toast';
import { useAuth } from './auth-context';
import * as api from './api';
import type { AlertNotification, QuoteResponse } from './types';

const WS_URL = process.env.NEXT_PUBLIC_WS_URL ?? 'http://localhost:8080/ws';

interface QuoteStreamContextValue {
  quotes: Record<string, QuoteResponse>;
  tickers: string[];
  isConnected: boolean;
  isLoading: boolean;
}

const QuoteStreamContext = createContext<QuoteStreamContextValue>({
  quotes: {},
  tickers: [],
  isConnected: false,
  isLoading: true,
});

function describeAlert(notification: AlertNotification): string {
  const direction = notification.condition === 'ABOVE' ? '이상' : '이하';
  return `${notification.ticker}이(가) ${notification.targetPrice} ${direction}에 도달했습니다 (현재가 ${notification.price})`;
}

/**
 * Fetches the active-symbol quote snapshot once, then keeps it live via a single
 * STOMP/SockJS connection subscribed to /topic/quotes/{ticker} for every active symbol.
 * The same connection also listens for admin symbol-set changes (/topic/symbols) and
 * this user's price-alert notifications (/user/queue/alerts).
 */
export function QuoteStreamProvider({ children }: { children: React.ReactNode }) {
  const { accessToken, authFetch } = useAuth();
  const showToast = useToast();
  const showToastRef = useRef(showToast);
  useEffect(() => {
    showToastRef.current = showToast;
  }, [showToast]);
  const [quotes, setQuotes] = useState<Record<string, QuoteResponse>>({});
  const [tickers, setTickers] = useState<string[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!accessToken) return undefined;
    let cancelled = false;
    api
      .getQuotes(authFetch)
      .then((initialQuotes) => {
        if (cancelled) return;
        const map: Record<string, QuoteResponse> = {};
        initialQuotes.forEach((quote) => {
          map[quote.symbol] = quote;
        });
        setQuotes(map);
        setTickers(initialQuotes.map((quote) => quote.symbol));
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [accessToken, authFetch]);

  useEffect(() => {
    if (!accessToken || tickers.length === 0) return undefined;

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL) as unknown as WebSocket,
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 5000,
      onConnect: () => {
        setIsConnected(true);
        tickers.forEach((ticker) => {
          client.subscribe(`/topic/quotes/${ticker}`, (message: IMessage) => {
            // Live ticks only carry symbol/price/volume/ts (no `name`) — merge onto the
            // REST snapshot instead of replacing it so `name` survives after the first tick.
            const tick = JSON.parse(message.body) as QuoteResponse;
            setQuotes((prev) => ({ ...prev, [tick.symbol]: { ...prev[tick.symbol], ...tick } }));
          });
        });
        client.subscribe('/topic/symbols', (message: IMessage) => {
          const activeTickers = JSON.parse(message.body) as string[];
          setTickers(activeTickers);
          api.getQuotes(authFetch).then((refreshedQuotes) => {
            const map: Record<string, QuoteResponse> = {};
            refreshedQuotes.forEach((quote) => {
              map[quote.symbol] = quote;
            });
            setQuotes(map);
          });
        });
        client.subscribe('/user/queue/alerts', (message: IMessage) => {
          const notification = JSON.parse(message.body) as AlertNotification;
          showToastRef.current({ body: describeAlert(notification), type: 'info', isAutoHide: false });
        });
      },
      onWebSocketClose: () => setIsConnected(false),
      onStompError: () => setIsConnected(false),
    });

    client.activate();

    return () => {
      setIsConnected(false);
      void client.deactivate();
    };
  }, [accessToken, tickers, authFetch]);

  return (
    <QuoteStreamContext.Provider value={{ quotes, tickers, isConnected, isLoading }}>
      {children}
    </QuoteStreamContext.Provider>
  );
}

export function useQuoteStream(): QuoteStreamContextValue {
  return useContext(QuoteStreamContext);
}
