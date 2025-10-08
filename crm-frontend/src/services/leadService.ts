export interface UpdateLeadStatusPayload {
  status: string;
}

/**
 * CRM frontend tarafında lead durumu güncelleme isteğini backend ile uyumlu
 * hale getirir. İstek gövdesi JSON olarak gönderilir.
 */
export async function updateLeadStatus(
  leadId: string,
  status: string,
  accessToken: string
) {
  const response = await fetch(`/api/leads/${leadId}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ status } satisfies UpdateLeadStatusPayload),
  });

  if (!response.ok) {
    const errorPayload = await response.json().catch(() => null);
    const message = errorPayload?.message ?? 'Lead status could not be updated.';
    throw new Error(message);
  }

  return response.json();
}
