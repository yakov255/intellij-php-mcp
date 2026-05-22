<?php

namespace App\Trait;

trait MetricsTrait
{
    private array $metrics = [];

    public function getMetrics(): array
    {
        return $this->metrics;
    }

    public function resetMetrics(): void
    {
        $this->metrics = [];
    }
}
