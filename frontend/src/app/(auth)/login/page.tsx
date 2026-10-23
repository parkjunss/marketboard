'use client';

import { Suspense, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import NextLink from 'next/link';
import { Center } from '@astryxdesign/core/Center';
import { VStack } from '@astryxdesign/core/Stack';
import { Card } from '@astryxdesign/core/Card';
import { Heading, Text } from '@astryxdesign/core/Text';
import { TextInput } from '@astryxdesign/core/TextInput';
import { Button } from '@astryxdesign/core/Button';
import { Banner } from '@astryxdesign/core/Banner';
import { RedirectIfAuthed } from '@/components/RedirectIfAuthed';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';
import { googleLoginUrl } from '@/lib/api';
import { clearLoginReturn, rememberLoginReturn, safeLoginReturn } from '@/lib/login-return';

function LoginForm() {
  const params = useSearchParams();
  const oauthError = params.get('oauthError');
  const destination = safeLoginReturn(params.get('next'));
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(
    oauthError ? 'Google 로그인에 실패했습니다. 다시 시도해 주세요.' : null,
  );
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      if (destination) rememberLoginReturn(destination);
      await login(email, password);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '로그인에 실패했습니다.');
      setIsSubmitting(false);
    }
  }

  return (
    <Center height="100vh">
      <VStack gap={4} width="min(360px, calc(100vw - 32px))">
        <VStack gap={1}>
          <Heading level={3}>MarketBoard</Heading>
          <Text type="body" color="secondary">
            {destination?.startsWith('/symbols/') ? `${destination.split('/')[2]} 상세를 보려면 로그인하세요. 로그인 후 원래 화면으로 돌아갑니다.` : '관심종목과 개인화 기능을 이용하려면 로그인하세요'}
          </Text>
        </VStack>
        <Card padding={8} width="100%">
          <form onSubmit={handleSubmit}>
            <VStack gap={4}>
              {error && <Banner status="error" title="로그인 실패" description={error} />}
              <TextInput
                type="email"
                label="이메일"
                value={email}
                onChange={setEmail}
                isRequired
                hasAutoFocus
              />
              <TextInput type="password" label="비밀번호" value={password} onChange={setPassword} isRequired />
              <Text type="body" size="sm"><NextLink href="/forgot-password">비밀번호를 잊으셨나요?</NextLink></Text>
              <Button type="submit" variant="primary" label="로그인" isLoading={isSubmitting} />
              <Button
                type="button"
                variant="secondary"
                label="Google로 계속하기"
                onClick={() => {
                  if (destination) rememberLoginReturn(destination);
                  window.location.assign(googleLoginUrl());
                }}
              />
            </VStack>
          </form>
        </Card>
        <Text type="body" size="sm">
          계정이 없으신가요? <NextLink href="/signup">회원가입</NextLink>
        </Text>
        <Text type="body" size="sm">
          <NextLink href="/overview" onClick={clearLoginReturn}>로그인 없이 시장 개요 먼저 보기 →</NextLink>
        </Text>
        {destination && <Text type="body" size="sm"><NextLink href="/stock-list" onClick={clearLoginReturn}>로그인 취소하고 종목 목록으로</NextLink></Text>}
      </VStack>
    </Center>
  );
}

export default function LoginPage() {
  return (
    <RedirectIfAuthed>
      <Suspense fallback={null}><LoginForm /></Suspense>
    </RedirectIfAuthed>
  );
}
