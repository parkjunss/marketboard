import { Center } from '@astryxdesign/core/Center';
import { VStack } from '@astryxdesign/core/Stack';
import { Heading, Text } from '@astryxdesign/core/Text';

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <VStack gap={2}>
      <Heading level={4}>{title}</Heading>
      <Text type="body">{children}</Text>
    </VStack>
  );
}

export default function TermsPage() {
  return (
    <Center axis="horizontal">
      <VStack gap={6} maxWidth={720} paddingBlock={10} paddingInline={6}>
        <VStack gap={1}>
          <Heading level={2}>이용약관</Heading>
          <Text type="body" color="secondary">
            최종 수정일: 2026-07-20
          </Text>
        </VStack>

        <Section title="1. 서비스 소개">
          MarketBoard는 개인이 학습 및 포트폴리오 목적으로 만든 프로젝트입니다. 등록된 금융투자업자나
          투자자문업자가 운영하는 상업적 서비스가 아니며, 회원가입 시 별도의 이용료를 받지 않습니다.
        </Section>

        <Section title="2. 투자 자문이 아닙니다">
          서비스에서 제공하는 시세, 기술지표, 재무제표, 포트폴리오 계산 결과는 모두 참고용 정보이며 투자
          권유나 투자자문이 아닙니다. 데이터는 외부 API(Finnhub 등)에서 수집하며 지연되거나 부정확할 수
          있습니다. 이 정보를 근거로 한 투자 판단과 그 결과에 대해 서비스 운영자는 책임을 지지 않습니다.
        </Section>

        <Section title="3. 계정">
          이메일과 비밀번호로 계정을 생성합니다. 부정 사용, 서비스 악용(과도한 요청 등)이 확인되면
          운영자가 계정을 정지하거나 삭제할 수 있습니다. 계정 삭제를 원하시면 운영자에게 요청해주세요.
        </Section>

        <Section title="4. 서비스 변경 및 중단">
          개인 프로젝트 특성상 사전 고지 없이 기능이 변경되거나 서비스 전체가 중단될 수 있습니다. 서비스
          중단에 따른 손해에 대해 운영자는 책임을 지지 않습니다.
        </Section>

        <Section title="5. 약관 변경">
          이 약관은 서비스 개선에 따라 변경될 수 있으며, 변경 시 이 페이지에 반영합니다.
        </Section>
      </VStack>
    </Center>
  );
}
