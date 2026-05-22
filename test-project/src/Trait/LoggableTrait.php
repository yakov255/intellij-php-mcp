<?php

namespace App\Trait;

trait LoggableTrait
{
    private array $logs = [];

    protected function log(string $message): void
    {
        $this->logs[] = '[' . date('H:i:s') . '] ' . $message;
    }

    public function getLogs(): array
    {
        return $this->logs;
    }
}
