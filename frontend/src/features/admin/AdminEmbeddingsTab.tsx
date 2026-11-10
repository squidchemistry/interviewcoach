import { useMutation } from '@tanstack/react-query';
import { adminContentApi } from '../../api/endpoints';
import { Alert } from '../../components/ui/form';
import { buttonClass } from '../../components/ui/styles';
import { describeError } from '../auth/errorMessages';

export function AdminEmbeddingsTab() {
  const reindex = useMutation({ mutationFn: adminContentApi.reindex });
  return (
    <div className="space-y-5">
      <h2 className="nb-heading text-2xl">Embeddings</h2>
      <p className="max-w-2xl font-medium text-muted">
        Question embeddings power "similar questions" and duplicate detection. They are computed in the background when questions
        change; reindex embeds every question that is missing an up-to-date embedding right now.
      </p>
      <button type="button" className={buttonClass.primary} disabled={reindex.isPending} onClick={() => reindex.mutate()}>
        {reindex.isPending ? 'Reindexing…' : 'Reindex embeddings'}
      </button>
      {reindex.isSuccess && (
        <Alert tone="success">
          Embedded {reindex.data.embedded} {reindex.data.embedded === 1 ? 'question' : 'questions'} with {reindex.data.model}.
        </Alert>
      )}
      {reindex.isError && <Alert>{describeError(reindex.error)}</Alert>}
    </div>
  );
}
