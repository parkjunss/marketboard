'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
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

function LoginForm() {
  const router = useRouter();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      await login(email, password);
      router.replace('/stock-list');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '로그인에 실패했습니다.');
      setIsSubmitting(false);
    }
  }

  return (
    <Center height="100vh">
      <VStack gap={4} width={360}>
        <VStack gap={1}>
          <Heading level={3}>MarketBoard</Heading>
          <Text type="body" color="secondary">
            실시간 시세를 확인하려면 로그인하세요
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
              <Button type="submit" variant="primary" label="로그인" isLoading={isSubmitting} />
            </VStack>
          </form>
        </Card>
        <Text type="body" size="sm">
          계정이 없으신가요? <NextLink href="/signup">회원가입</NextLink>
        </Text>
      </VStack>
    </Center>
  );
}

export default function LoginPage() {
  return (
    <RedirectIfAuthed>
      <LoginForm />
    </RedirectIfAuthed>
  );
}
